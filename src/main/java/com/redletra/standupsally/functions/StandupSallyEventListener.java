package com.redletra.standupsally.functions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.secretmanager.v1.*;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redletra.standupsally.slack.StandupSallySlackApiInvoker;
import com.redletra.standupsally.utils.Constants;
import com.redletra.standupsally.utils.InvalidAppRequestException;
import com.redletra.standupsally.utils.SecretUtils;
import com.redletra.standupsally.utils.Utils;
import org.javatuples.Pair;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.redletra.standupsally.utils.Constants.*;
import static com.redletra.standupsally.utils.Utils.generateSecretStringFromChannelIdToUserHandlesListMap;

public class StandupSallyEventListener implements HttpFunction {

    private static final Gson gson = new Gson();
    private final SecretUtils secretUtils;
    private final StandupSallySlackApiInvoker standupSallySlackApiInvoker;

    public StandupSallyEventListener() {
        this.standupSallySlackApiInvoker = new StandupSallySlackApiInvoker();
        this.secretUtils = new SecretUtils();
    }

    public StandupSallyEventListener(SecretUtils secretUtils, StandupSallySlackApiInvoker standupSallySlackApiInvoker) {
        this.standupSallySlackApiInvoker = standupSallySlackApiInvoker;
        this.secretUtils = secretUtils;
    }

    /*
        map of slack event type to a Consumer which process the event
     */
    private final Map<String, BiConsumer<JsonObject, SecretManagerServiceClient>> eventProcessorMap
            = Map.of("member_joined_channel", this::processMemberJoinedEvent,
                    "app_mention", this::processAppMentionEvent,
                    "member_left_channel", this::processMemberLeftEvent);

    public boolean validateRequest(JsonObject body,
                                   String slackSigningSecret,
                                   String slackSignatureHeader,
                                   String slackRequestTimestamp) {

        String baseString = Constants.SLACK_VERSION_NUMBER + ":" + slackRequestTimestamp + ":" + body.toString();
        SecretKeySpec secretKeySpec = new SecretKeySpec(slackSigningSecret.getBytes(), Constants.HMAC_ALGORITHM);
        try {
            Mac mac = Mac.getInstance(Constants.HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            String calculatedSignature = "v0=" + BaseEncoding.base16().encode(mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8))).toLowerCase();

            return calculatedSignature.equals(slackSignatureHeader);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return false;
        } catch (InvalidKeyException e) {
            return false;
        }
    }

    @Override
    public void service(HttpRequest httpRequest, HttpResponse httpResponse) throws Exception {

        BufferedWriter writer = httpResponse.getWriter();
        BufferedReader reader = httpRequest.getReader();
        System.out.println("begin request " + LocalDateTime.now());
        JsonObject body = gson.fromJson(reader, JsonObject.class);
        // challenge request
        if( body.has("challenge")) {
            System.out.println("has challenge request");
            JsonElement challenge = body.get("challenge");
            httpResponse.setStatusCode(200);
            httpResponse.setContentType("text/plain");
            writer.write(challenge.toString());

        } else {
            // slack expects response within 3 seconds. Otherwise it will consider
            // it a fail and resend the request. Ideally we would send a response immediately
            // and process request in a separate thread. However, this doesn't seem to work in
            // GCP for some reason. So just do it in the main thread and ignore any slack retry attempts
            // If we do not ignore the retries we can get data corruption in the GCP secrets.
            if (!Utils.getSlackRetryNumHeader(httpRequest).isPresent()) {

                Optional<String> slackSignature = Utils.getSlackSignatureHeader(httpRequest);
                slackSignature.ifPresentOrElse(slackSignatureHeader -> {
                    Utils.getSlackTimestampHeader(httpRequest)
                        .ifPresentOrElse(slackReqTimestampHeader -> {
                            try {
                                System.out.println("call processRequest");
                                processRequest(body,
                                        slackSignatureHeader,
                                        slackReqTimestampHeader);
                            } catch (Exception e) {
                                System.out.println("process request thread caught exception " + e.getMessage());
                                e.printStackTrace();
                            }
                        }, () -> System.out.println("no slack request timestamp found"));

                }, () -> System.out.println("no slack signature found"));
            } else {
                System.out.println("Slack retry detected....end here");
            }
        }
        httpResponse.setStatusCode(200);
        System.out.println("Return from event processing " + LocalDateTime.now());

    }

    /*
       process the slack event for which we have subscribed
     */
    void processRequest(JsonObject body,
                                String slackSignatureHeader,
                                String slackRequestTimestampHeader) throws IOException {
        //validate
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {

            System.out.println("body is " + body);
            // get the signing secret used to calculate the signature
            SecretVersionName signingSecretSecretVersionName = SecretVersionName.of(PROJECT_ID, SLACK_SIGNING_SECRET_NAME, "latest");
            AccessSecretVersionResponse signingSecretSecretVersionResponse = client.accessSecretVersion(signingSecretSecretVersionName);
            String slackSigningSecret = signingSecretSecretVersionResponse.getPayload().getData().toStringUtf8();

            if (validateRequest(body,
                    slackSigningSecret,
                    slackSignatureHeader,
                    slackRequestTimestampHeader)) {
                System.out.println("valid request");
                if (body.has("event")) {
                    System.out.println("request has event");
                    JsonObject event = body.getAsJsonObject("event");
                    if(event.has("type")) {
                        JsonElement typeElem = event.get("type");
                        String typeValue = typeElem.getAsString();
                        System.out.println("event type is " + typeValue);
                        eventProcessorMap.get(typeValue).accept(event, client);
                    }
                }

            } else {
                System.out.println("invalid request");
                throw new InvalidAppRequestException();
            }
        }
    }

    void processAppMentionEvent(JsonObject appMentionEvent,
                              SecretManagerServiceClient client) {

        String appMentionContent = appMentionEvent.get("text").getAsString();
        String channelId = appMentionEvent.get("channel").getAsString();

        //if text is "add us" then add to users to standup sally secrets otherwise do nothing (note if order is important)
        if(appMentionContent.toLowerCase().contains("add us")) {
            Utils.getSallyUserFromAppMentionEvent(appMentionContent).ifPresent(
                    sallyUser -> addChannelUsersToSally(channelId, client, sallyUser));
        } else if (appMentionContent.toLowerCase().contains("remove")) {
            //remove user request received
            Utils.getUserFromAppMentionEvent(appMentionContent).
                    ifPresent(userToRemoveFromStandupSally ->
                        removeUserFromChannel(userToRemoveFromStandupSally,
                            channelId,
                            client));
        } else if (appMentionContent.toLowerCase().contains("add")) {
            //add user request received
            Utils.getUserFromAppMentionEvent(appMentionContent).
                    ifPresent(userToAddToStandupSally ->
                         addUserToChannel(userToAddToStandupSally,
                            channelId,
                            client));
        }
    }

    /**
     * Get all the users in the channel and add them to the relevant secrets. Don't add sally herself as a user
     * though (she may be included as a user of the slack channel)
     * @param channelId
     * @param client
     * @param sallyUser
     */
    void addChannelUsersToSally(String channelId,
                                        SecretManagerServiceClient client,
                                        String sallyUser) {
        Pair<Map<String, List<String>>, SecretVersion> channelIdToUserListMap = this.secretUtils.getChannelIdToUserListMap(client);
        Map<String, List<String>> channelIdToUserList = channelIdToUserListMap.getValue0();
        SecretVersion channelIdToUserListSecretVersion = channelIdToUserListMap.getValue1();

        //look up members of channel (don't incude sally user herself)
        String slackOauthToken = secretUtils.getSlackToken(client);
        List<String> slackUsersForChannelArray = this.standupSallySlackApiInvoker.
                getSlackUsersForChannel(channelId, slackOauthToken)
                .stream()
                    .filter(user -> !("<@"+user+">").equals(sallyUser))
                    .collect(Collectors.toList());

        //add them to the channel to user list secret
        channelIdToUserList.put(channelId, slackUsersForChannelArray);
        String newChannelIdToUserHandlesMap = generateSecretStringFromChannelIdToUserHandlesListMap(channelIdToUserList);
        this.secretUtils.createNewSecretVersionAndDeleteOld(newChannelIdToUserHandlesMap,
                client,
                channelIdToUserListSecretVersion,
                CHANNEL_ID_TO_MEMBER_HANDLES_SECRET_NAME);

        //add the first user in the list as the person to next run standup for the channel
        Pair<Map<String, String>, SecretVersion> userForEachChannelWhoLastRanStandup = this.secretUtils.getUserForEachChannelWhoLastRanStandup(client);

        //set the person to run standup as the first person in the list
        Map<String, String> userForEachChannelWhoLastRanStandupMap = userForEachChannelWhoLastRanStandup.getValue0();
        userForEachChannelWhoLastRanStandupMap.put(channelId, slackUsersForChannelArray.get(0));

        String updatedSecretValue = Utils.generateSecretStringFromChannelIdToLastUserToRunStandupMap(userForEachChannelWhoLastRanStandupMap);
        this.secretUtils.createNewSecretVersionAndDeleteOld(
                updatedSecretValue,
                client,
                userForEachChannelWhoLastRanStandup.getValue1(),
                LAST_USER_FOR_EACH_CHANNEL_TO_RUN_STANDUP_SECRET_NAME
        );

        String message = "Standup Sally added " + Utils.generateSlackUsersString(slackUsersForChannelArray) + " for standup duties";
        this.standupSallySlackApiInvoker.appMentionActionFeedback(message,
                slackOauthToken,
                channelId);
    }

    /*
        remove the user from the list of slack users in the channel
     */
    void processMemberLeftEvent(JsonObject memberLeftChannelEvent,
                                          SecretManagerServiceClient client) {
        String channelToRemoveUserFrom = memberLeftChannelEvent.get("channel").getAsString();
        String userIdToRemove = memberLeftChannelEvent.get("user").getAsString();

        removeUserFromChannel(userIdToRemove,
                channelToRemoveUserFrom,
                client);
    }

    /*
       add the newly added slack user to the CHANNEL_ID_TO_MEMBER_HANDLES_SECRET_NAME secret
       and delete the old secret version
     */
    void processMemberJoinedEvent(JsonObject memberJoinedChannelEvent,
                              SecretManagerServiceClient client) {
        String channelToAddUserTo = memberJoinedChannelEvent.get("channel").getAsString();
        String userIdToAdd = memberJoinedChannelEvent.get("user").getAsString();
        addUserToChannel(userIdToAdd,
                channelToAddUserTo,
                client);
    }

    /*
        either remove or add a user from a channels list of users. Use the supplied
        Function to supply Add or Remove functionality. If the user does not exist
        in the channel do not do anything
     */
    void updateUserIdInChannel(String channelToAddOrRemoveUserFrom,
                                       SecretManagerServiceClient client,
                                       Function<List<String>, List<String>> howToProcessAdditionOrDeletionFromList) {
        Pair<Map<String, List<String>>, SecretVersion> pair = this.secretUtils.getChannelIdToUserListMap(client);
        Map<String, List<String>> channelIdToUserListMap = pair.getValue0();
        SecretVersion slackChannelIdToMemberHandlesSecretVersion = pair.getValue1();
        Optional.ofNullable(channelIdToUserListMap.get(channelToAddOrRemoveUserFrom))
                .map(howToProcessAdditionOrDeletionFromList)
                .map(userListWithNewUser -> {
                    channelIdToUserListMap.replace(channelToAddOrRemoveUserFrom, userListWithNewUser);
                    return channelIdToUserListMap;
                }).ifPresent(updatedChannelIdToUserListMap ->
                    updateChannelIdToUserListSecret(client,
                            updatedChannelIdToUserListMap,
                            slackChannelIdToMemberHandlesSecretVersion)
                );
    }
    /*
       update the secret CHANNEL_ID_TO_MEMBER_HANDLES_SECRET_NAME with new value, then delete the old secret value
     */
    private void updateChannelIdToUserListSecret(SecretManagerServiceClient client,
                                                 Map<String, List<String>> updatedChannelIdToUserListMap,
                                                 SecretVersion slackChannelIdToMemberHandlesSecretVersion) {
        String newChannelIdToUserListMapString = generateSecretStringFromChannelIdToUserHandlesListMap(updatedChannelIdToUserListMap);
        this.secretUtils.createNewSecretVersionAndDeleteOld(newChannelIdToUserListMapString,
                client,
                slackChannelIdToMemberHandlesSecretVersion,
                CHANNEL_ID_TO_MEMBER_HANDLES_SECRET_NAME);
    }

    void addUserToChannel(String userIdToAdd,
                          String channelToAddUserTo,
                          SecretManagerServiceClient client) {
        Function<List<String>, List<String>> howToProcessAddition = userListForChannel -> {
            if (!userListForChannel.contains(userIdToAdd)) {
                userListForChannel.add(userIdToAdd);
                return userListForChannel;
            } else {
                return null;
            }
        };

        updateUserIdInChannel(channelToAddUserTo,
                client,
                howToProcessAddition);
        String slackAuthToken = this.secretUtils.getSlackToken(client);
        this.standupSallySlackApiInvoker.appMentionActionFeedback("Standup Sally says welcome <@" +userIdToAdd +">",
                slackAuthToken,
                channelToAddUserTo);
    }

    void removeUserFromChannel(String userToRemoveFromStandupSally,
                               String channelId,
                               SecretManagerServiceClient client) {
        Function<List<String>, List<String>> howToProcessRemovalOfUser = userListForChannel -> {
            if(userListForChannel.contains(userToRemoveFromStandupSally)) {
                userListForChannel.remove(userToRemoveFromStandupSally);
                return userListForChannel;
            } else {
                return null;
            }
        };

        updateUserIdInChannel(channelId,
                client,
                howToProcessRemovalOfUser);
        String slackAuthToken = this.secretUtils.getSlackToken(client);
        this.standupSallySlackApiInvoker.appMentionActionFeedback("Standup Sally says bye bye <@" +userToRemoveFromStandupSally +">",
                slackAuthToken,
                channelId);
    }

}
