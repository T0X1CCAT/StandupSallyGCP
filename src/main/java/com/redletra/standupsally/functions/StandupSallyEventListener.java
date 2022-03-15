package com.redletra.standupsally.functions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.secretmanager.v1.*;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.redletra.standupsally.utils.Constants.*;

public class StandupSallyEventListener implements HttpFunction {

    private static final Gson gson = new Gson();
    private final SecretUtils secretUtils;

    public StandupSallyEventListener() {
        this.secretUtils = new SecretUtils();
    }

    public StandupSallyEventListener(SecretUtils secretUtils) {
        this.secretUtils = secretUtils;
    }

    /*
        map of slack event type to a Consumer which process the event
     */
    private final Map<String, BiConsumer<JsonObject, SecretManagerServiceClient>> eventProcessorMap
            = Map.of("member_joined_channel", this::processMemberJoinedEvent,
                    "app_mention", this::processAppMentionEvent,
                    "member_left_channel", this::processMemberLeftEvent);

    private static final String SLACK_VERSION_NUMBER = "v0";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Override
    public void service(HttpRequest httpRequest, HttpResponse httpResponse) throws Exception {

        BufferedWriter writer = httpResponse.getWriter();
        BufferedReader reader = httpRequest.getReader();
        try {
            String contentType = httpRequest.getContentType().orElseThrow(InvalidAppRequestException::new);
            System.out.println("content type " + contentType);
            //validate
            if ("application/json".equals(contentType)) {

                try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {

                    // get the signing secret used to calculate the signature
                    SecretVersionName signingSecretSecretVersionName = SecretVersionName.of(PROJECT_ID, SLACK_SIGNING_SECRET_NAME, "latest");
                    AccessSecretVersionResponse signingSecretSecretVersionResponse = client.accessSecretVersion(signingSecretSecretVersionName);
                    String slackSigningSecret = signingSecretSecretVersionResponse.getPayload().getData().toStringUtf8();

                    JsonObject body = gson.fromJson(reader, JsonObject.class);
                    if (validateRequest(httpRequest, body, slackSigningSecret)) {
                        System.out.println("valid request");

                        // challenge request
                        if( body.has("challenge")) {
                            System.out.println("has challenge request");
                            JsonElement challenge = body.get("challenge");
                            httpResponse.setStatusCode(200);
                            httpResponse.setContentType("text/plain");
                            writer.write(challenge.toString());
                        } else {
                            httpResponse.setStatusCode(200);

                            // do other stuff in it's own thread because it might take more than the 3 sec slack timeout
                            Executors.newSingleThreadExecutor().submit(() -> {
                                try{
                                    processRequest(body);
                                } catch(Exception e) {
                                    System.out.println("process request thread caught exception " + e.getMessage());
                                    e.printStackTrace();
                                }
                            });
                        }
                    } else {
                        System.out.println("invalid request");
                        throw new InvalidAppRequestException();
                    }
                }
            }
        } catch(InvalidAppRequestException iare) {
            System.out.println("invalid content type " + iare.getMessage());
            httpResponse.setStatusCode(400);
            writer.write("invalid request");
        }

    }

    /*
       process the slack event for which we have subscribed
     */
    private void processRequest(JsonObject body) throws IOException {

        if (body.has("event")) {
            JsonObject event = body.getAsJsonObject("event");
            if(event.has("type")) {
                JsonElement typeElem = event.get("type");
                String typeValue = typeElem.getAsString();
                try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
                    eventProcessorMap.get(typeValue).accept(event, client);
                }
            }
        }
    }

    private void processAppMentionEvent(JsonObject memberJoinedChannelEvent,
                              SecretManagerServiceClient client) {
        //if text is "add us" then add to users to standup sally secrets otherwise do nothing

        // get the channel id
        // get the user handles
        // save above to CHANNEL_ID_TO_MEMBER_HANDLES_SECRET_NAME secret and delete old version

        // save to LAST_USER_FOR_EACH_CHANNEL_TO_RUN_STANDUP_SECRET_NAME version using the first user handle and delete old version

    }

    /*
        remove the user from the list of slack users in the channel
     */
    void processMemberLeftEvent(JsonObject memberLeftChannelEvent,
                                          SecretManagerServiceClient client) {
        String channelToRemoveUserFrom = memberLeftChannelEvent.get("channel").getAsString();
        String userIdToRemove = memberLeftChannelEvent.get("user").getAsString();

        Function<List<String>, List<String>> howToProcessRemovalOfUser = userListForChannel -> {
            if(userListForChannel.contains(userIdToRemove)) {
                userListForChannel.remove(userIdToRemove);
                return userListForChannel;
            } else {
                return null;
            }
        };

        updateUserIdInChannel(channelToRemoveUserFrom,
                client,
                howToProcessRemovalOfUser);
    }

    /*
       add the newly added slack user to the CHANNEL_ID_TO_MEMBER_HANDLES_SECRET_NAME secret
       and delete the old secret version
     */
    void processMemberJoinedEvent(JsonObject memberJoinedChannelEvent,
                              SecretManagerServiceClient client) {
        String channelToAddUserTo = memberJoinedChannelEvent.get("channel").getAsString();
        String userIdToAdd = memberJoinedChannelEvent.get("user").getAsString();

        Function<List<String>, List<String>> howToProcessAddition = userListForChannel -> {
            if(!userListForChannel.contains(userIdToAdd)) {
                userListForChannel.add(userIdToAdd);
                return userListForChannel;
            } else {
                return null;
            }
        };

        updateUserIdInChannel(channelToAddUserTo,
                client,
                howToProcessAddition);
    }

    /*
        either remove or add a user from a channels list of users. Use the supplied
        Function to supply Add or Remove functionality. If the user does not exist
        in the channel do not do anything
     */
    private void updateUserIdInChannel(String channelToAddOrRemoveUserFrom,
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
        String newChannelIdToUserListMapString = Utils.generateSecretStringFromChannelIdToUserHandlesListMap(updatedChannelIdToUserListMap);
        this.secretUtils.createNewSecretVersionAndDeleteOld(newChannelIdToUserListMapString,
                client,
                slackChannelIdToMemberHandlesSecretVersion,
                CHANNEL_ID_TO_MEMBER_HANDLES_SECRET_NAME);
    }

    boolean validateRequest(HttpRequest httpRequest, JsonObject body, String slackSigningSecret) {

        Optional<String> slackSignature = httpRequest.getFirstHeader("X-Slack-Signature");
        return slackSignature.map(signature -> {
            Optional<String> slackRequestTimestamp = httpRequest.getFirstHeader("X-Slack-Request-Timestamp");
            return slackRequestTimestamp.map(requestTimestamp -> {
                String baseString = SLACK_VERSION_NUMBER + ":" + requestTimestamp + ":" + body.toString();
                SecretKeySpec secretKeySpec = new SecretKeySpec(slackSigningSecret.getBytes(), HMAC_ALGORITHM);
                try {
                    Mac mac = Mac.getInstance(HMAC_ALGORITHM);
                    mac.init(secretKeySpec);
                    String calculatedSignature = "v0=" + BaseEncoding.base16().encode(mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8))).toLowerCase();

                    return calculatedSignature.equals(signature);
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                    return false;
                } catch (InvalidKeyException e) {
                    return false;
                }
            }).orElse(false);
        }).orElse(false);

    }


}
