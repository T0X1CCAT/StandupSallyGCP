package com.redletra.standupsally.functions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.secretmanager.v1.*;
import com.google.protobuf.ByteString;
import com.redletra.standupsally.slack.StandupSallySlackApiInvoker;
import com.redletra.standupsally.utils.Constants;
import com.redletra.standupsally.utils.Utils;
import org.javatuples.Pair;

import java.io.BufferedWriter;
import java.time.*;
import java.util.List;
import java.util.Map;

import static com.redletra.standupsally.utils.Utils.getNextUserHandles;

public class StandupSally implements HttpFunction {

    private static final String SLACK_OAUTH_TOKEN_SECRET_NAME = "slackOauthToken";

    private static final String LAST_USER_FOR_EACH_CHANNEL_TO_RUN_STANDUP_SECRET_NAME = "channelTolastUserToRunStandup";

    private static final String CHANNEL_ID_TO_MEMBER_HANDLES_SECRET_NAME = "channelIdToMemberSlackHandles";

    private StandupSallySlackApiInvoker standupSallySlackApiInvoker;

    public StandupSally() {
        this.standupSallySlackApiInvoker = new StandupSallySlackApiInvoker();
    }

    // uses an HTTP request - would be better to use Pub/Sub
    public void service(final HttpRequest request, final HttpResponse response) throws Exception {

        Instant nowUtc = Instant.now();
        ZoneId ausSydney = ZoneId.of("Australia/Sydney");
        ZonedDateTime nowAusSydney = ZonedDateTime.ofInstant(nowUtc, ausSydney);

        if (nowAusSydney.getDayOfWeek().equals(DayOfWeek.SATURDAY) || nowAusSydney.getDayOfWeek().equals(DayOfWeek.SUNDAY)) {
            final BufferedWriter writer = response.getWriter();
            writer.write("Done- weekend!");
        }

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {

            // slack oauthtoken
            String slackOauthToken = getSlackToken(client);

            // channel members eg channeid1=@tom,@steve;channelid2=@dave,@susy,@chris....
            // (secret has payload max of 64Kb so we should be good for a few teams
            Map<String, List<String>> channelIdToUserListMap = getChannelIdToUserListMap(client);

            // last slack handle to run standup
            Pair<Map<String, String>, SecretVersion> lastUserForEachChannelToRunStandupAndSecretVersionPair = getUserForEachChannelWhoLastRanStandup(client);
            Map<String, String> channelIdToUserWhoLastRanStandupMap = lastUserForEachChannelToRunStandupAndSecretVersionPair.getValue0();
            // need this because we will have to delete the old version after we update the user who will run standup today
            SecretVersion channelIdToUserWhoLastRanStandupSecretVersion = lastUserForEachChannelToRunStandupAndSecretVersionPair.getValue1();

            Map<String, String> nextUserHandleForEachChannelToRunStandup = getNextUserHandles(channelIdToUserListMap,
                channelIdToUserWhoLastRanStandupMap);

            updateLastUserSecret(client,
                    nextUserHandleForEachChannelToRunStandup,
                    Constants.PROJECT_ID,
                    LAST_USER_FOR_EACH_CHANNEL_TO_RUN_STANDUP_SECRET_NAME,
                    channelIdToUserWhoLastRanStandupSecretVersion);

            final BufferedWriter writer = response.getWriter();
            writer.write("Done!");

            standupSallySlackApiInvoker.invokeApi(nextUserHandleForEachChannelToRunStandup, slackOauthToken);

        }

    }

    private Pair<Map<String,String>, SecretVersion> getUserForEachChannelWhoLastRanStandup(SecretManagerServiceClient client) {
        SecretVersionName lastUserForEachChannelToRunStandupSecretVersionName = SecretVersionName.of(Constants.PROJECT_ID, LAST_USER_FOR_EACH_CHANNEL_TO_RUN_STANDUP_SECRET_NAME, "latest");
        SecretVersion lastUserForEachChannelToRunStandupSecretVersion = client.getSecretVersion(lastUserForEachChannelToRunStandupSecretVersionName);

        AccessSecretVersionResponse lastUserForEachChannelToRunStandupSecretVersionResponse = client.accessSecretVersion(lastUserForEachChannelToRunStandupSecretVersionName);
        String lastUserHandleForEachChannelToRunStandup = lastUserForEachChannelToRunStandupSecretVersionResponse.getPayload().getData().toStringUtf8();
        Map<String, String> channelIdToUserWhoLastRanStandupMap = Utils.convertChannelIdToUserHandleStringToMap(lastUserHandleForEachChannelToRunStandup);
        return new Pair(channelIdToUserWhoLastRanStandupMap ,lastUserForEachChannelToRunStandupSecretVersion);
    }

    /*
    retrieve a string like channeid1=@tom,@steve;channelid2=@dave,@susy,@chris.... and convert it to a map of
    Channel Id to Channel User handles, ie Map<String, List<String>>
     */
    private Map<String, List<String>> getChannelIdToUserListMap(SecretManagerServiceClient client) {
        SecretVersionName slackChannelIdToMemberHandlesSecretVersionName = SecretVersionName.of(Constants.PROJECT_ID, CHANNEL_ID_TO_MEMBER_HANDLES_SECRET_NAME, "latest");
        AccessSecretVersionResponse slackChannelIdToMemberHandlesSecretVersionResponse = client.accessSecretVersion(slackChannelIdToMemberHandlesSecretVersionName);
        String slackChannelIdToMemberHandlesCommaSepString = slackChannelIdToMemberHandlesSecretVersionResponse.getPayload().getData().toStringUtf8();
        return Utils.convertChannelToUsersStringToMap(slackChannelIdToMemberHandlesCommaSepString);
    }

    /*
    get the slack token from secret
     */
    private String getSlackToken(SecretManagerServiceClient client) {
        SecretVersionName slackOauthTokenSecretVersionName = SecretVersionName.of(Constants.PROJECT_ID, SLACK_OAUTH_TOKEN_SECRET_NAME, "latest");
        AccessSecretVersionResponse slackOauthTokenSecretVersionResponse = client.accessSecretVersion(slackOauthTokenSecretVersionName);
        return slackOauthTokenSecretVersionResponse.getPayload().getData().toStringUtf8();
    }

    /*
      for each channel id in the map nextUserHandleToRunStandup, update the secret which stores the
      last user to run standup in each channel
     */
    private void updateLastUserSecret(SecretManagerServiceClient client,
                                      Map<String, String> channelIdToNextUserHandleToRunStandupMap,
                                      String projectId, String secretId,
                                      SecretVersion lastUserToRunStandupSecretVersionName) {

        //generate the new secret string from map nextUserHandleToRunStandup
        String channelIdToUserHandleToRunStandupSecretString = Utils.generateSecretStringFromMap(channelIdToNextUserHandleToRunStandupMap);
        SecretName secretName = SecretName.of(projectId, secretId);
        SecretPayload payload =
                SecretPayload.newBuilder()
                        .setData(ByteString.copyFromUtf8(channelIdToUserHandleToRunStandupSecretString))
                        .build();
        client.addSecretVersion(secretName, payload);

        //now delete the old version because suspect this is accruing costs (6 versions allowed in free tier)
        DestroySecretVersionRequest destroySecretVersionRequest = DestroySecretVersionRequest.newBuilder()
                .setName(lastUserToRunStandupSecretVersionName.getName())
                .build();

        client.destroySecretVersion(destroySecretVersionRequest);
    }

}
