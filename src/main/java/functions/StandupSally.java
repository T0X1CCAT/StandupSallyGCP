package functions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.secretmanager.v1.*;
import com.google.protobuf.ByteString;

import java.io.BufferedWriter;
import java.time.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StandupSally implements HttpFunction {

    private static final String PROJECT_ID = "standupsally";
    private static final String CHANNEL_ID_SECRET_NAME = "channelId";
    private static final String SLACK_OAUTH_TOKEN_SECRET_NAME = "slackOauthToken";
    private static final String LAST_USER_TO_RUN_STANDUP_SECRET_NAME = "lastUserToRunStandup";
    private static final String CHANNEL_MEMBER_HANDLES_SECRET_NAME = "channelMemberSlackHandles";

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

            // channel id
            SecretVersionName channelIdSecretVersionName = SecretVersionName.of(PROJECT_ID, CHANNEL_ID_SECRET_NAME, "latest");
            AccessSecretVersionResponse channelIdSecretVersionResponse = client.accessSecretVersion(channelIdSecretVersionName);
            String channelId = channelIdSecretVersionResponse.getPayload().getData().toStringUtf8();

            // slack oauthtoken
            SecretVersionName slackOauthTokenSecretVersionName = SecretVersionName.of(PROJECT_ID, SLACK_OAUTH_TOKEN_SECRET_NAME, "latest");
            AccessSecretVersionResponse slackOauthTokenSecretVersionResponse = client.accessSecretVersion(slackOauthTokenSecretVersionName);
            String slackOauthToken = slackOauthTokenSecretVersionResponse.getPayload().getData().toStringUtf8();

            // channel members
            SecretVersionName slackChannelMemberHandlesSecretVersionName = SecretVersionName.of(PROJECT_ID, CHANNEL_MEMBER_HANDLES_SECRET_NAME, "latest");
            AccessSecretVersionResponse slackChannelMemberHandlesSecretVersionResponse = client.accessSecretVersion(slackChannelMemberHandlesSecretVersionName);
            String slackChannelMemberHandlesCommaSepString = slackChannelMemberHandlesSecretVersionResponse.getPayload().getData().toStringUtf8();
            List <String> channelMemberHandles = Stream.of(slackChannelMemberHandlesCommaSepString
                    .split(",", -1))
                        .collect(Collectors.toList());

            // last slack handle to run standup
            SecretVersionName lastUserToRunStandupSecretVersionName = SecretVersionName.of(PROJECT_ID, LAST_USER_TO_RUN_STANDUP_SECRET_NAME, "latest");
            SecretVersion lastUserToRunStandupSecretVersion = client.getSecretVersion(lastUserToRunStandupSecretVersionName);

            AccessSecretVersionResponse lastUserToRunSecretVersionResponse = client.accessSecretVersion(lastUserToRunStandupSecretVersionName);
            String lastUserHandleToRunStandup = lastUserToRunSecretVersionResponse.getPayload().getData().toStringUtf8();

            String nextUserHandleToRunStandup = getNextUserHandle(channelMemberHandles, lastUserHandleToRunStandup);

            updateLastUserSecret(client,
                    nextUserHandleToRunStandup,
                    PROJECT_ID,
                    LAST_USER_TO_RUN_STANDUP_SECRET_NAME,
                    lastUserToRunStandupSecretVersion);
            final BufferedWriter writer = response.getWriter();
            writer.write("Done!" + nextUserHandleToRunStandup);

            String message = "Standup Sally says today it's <" + nextUserHandleToRunStandup + ">'s turn to rule standup";
            standupSallySlackApiInvoker.invokeApi(channelId, message, slackOauthToken);
        }




    }

    private void updateLastUserSecret(SecretManagerServiceClient client,
                                      String nextUserHandleToRunStandup,
                                      String projectId, String secretId,
                                      SecretVersion lastUserToRunStandupSecretVersionName) {
        SecretName secretName = SecretName.of(projectId, secretId);
        SecretPayload payload =
                SecretPayload.newBuilder()
                        .setData(ByteString.copyFromUtf8(nextUserHandleToRunStandup))
                        .build();
        client.addSecretVersion(secretName, payload);

        //now delete the old version because suspect this is accruing costs (6 versions allowed in free tier)
        DestroySecretVersionRequest destroySecretVersionRequest = DestroySecretVersionRequest.newBuilder()
                .setName(lastUserToRunStandupSecretVersionName.getName())
                .build();

        client.destroySecretVersion(destroySecretVersionRequest);
    }

    private String getNextUserHandle(List<String> channelMemberHandles, String lastUser) {
        int index = channelMemberHandles.indexOf(lastUser);
        if (index == (channelMemberHandles.size()-1)) {
            index = 0;
        } else {
            index++;
        }
        return channelMemberHandles.get(index);
    }
}
