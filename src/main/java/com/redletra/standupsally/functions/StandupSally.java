package com.redletra.standupsally.functions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.secretmanager.v1.*;
import com.redletra.standupsally.slack.StandupSallySlackApiInvoker;
import com.redletra.standupsally.utils.Constants;
import com.redletra.standupsally.utils.SecretUtils;
import com.redletra.standupsally.utils.Utils;
import org.javatuples.Pair;

import java.io.BufferedWriter;
import java.time.*;
import java.util.List;
import java.util.Map;

public class StandupSally implements HttpFunction {

    private StandupSallySlackApiInvoker standupSallySlackApiInvoker;
    private SecretUtils secretUtils;

    /*
        for testing
     */
    public StandupSally(SecretUtils secretUtils,
                        StandupSallySlackApiInvoker standupSallySlackApiInvoker) {
        this.secretUtils = secretUtils;
        this.standupSallySlackApiInvoker = standupSallySlackApiInvoker;
    }

    public StandupSally() {
        this.standupSallySlackApiInvoker = new StandupSallySlackApiInvoker();
        this.secretUtils = new SecretUtils();
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
            String slackOauthToken = secretUtils.getSlackToken(client);

            // channel members eg channeid1=@tom,@steve;channelid2=@dave,@susy,@chris....
            // (secret has payload max of 64Kb so we should be good for a few teams
            Map<String, List<String>> channelIdToUserListMap = this.secretUtils.getChannelIdToUserListMap(client).getValue0();

            // last slack handle to run standup
            Pair<Map<String, String>, SecretVersion> lastUserForEachChannelToRunStandupAndSecretVersionPair =
                    secretUtils.getUserForEachChannelWhoLastRanStandup(client);

            Map<String, String> channelIdToUserWhoLastRanStandupMap = lastUserForEachChannelToRunStandupAndSecretVersionPair.getValue0();
            // need this because we will have to delete the old version after we update the user who will run standup today
            SecretVersion channelIdToUserWhoLastRanStandupSecretVersion = lastUserForEachChannelToRunStandupAndSecretVersionPair.getValue1();

            Map<String, String> nextUserHandleForEachChannelToRunStandup = Utils.getNextUserHandles(channelIdToUserListMap,
                channelIdToUserWhoLastRanStandupMap);

            updateLastUserSecret(client,
                    nextUserHandleForEachChannelToRunStandup,
                    channelIdToUserWhoLastRanStandupSecretVersion);

            final BufferedWriter writer = response.getWriter();
            writer.write("Done!");

            standupSallySlackApiInvoker.informStandupRunner(nextUserHandleForEachChannelToRunStandup, slackOauthToken);
        }
    }

    /*
      for each channel id in the map nextUserHandleToRunStandup, update the secret which stores the
      last user to run standup in each channel
     */
    void updateLastUserSecret(SecretManagerServiceClient client,
                                      Map<String, String> channelIdToNextUserHandleToRunStandupMap,
                                      SecretVersion lastUserToRunStandupSecretVersionName) {

        //generate the new secret string from map nextUserHandleToRunStandup
        String channelIdToUserHandleToRunStandupSecretString = Utils.generateSecretStringFromChannelIdToLastUserToRunStandupMap(channelIdToNextUserHandleToRunStandupMap);
        this.secretUtils.createNewSecretVersionAndDeleteOld(channelIdToUserHandleToRunStandupSecretString,
                client,
                lastUserToRunStandupSecretVersionName,
                Constants.LAST_USER_FOR_EACH_CHANNEL_TO_RUN_STANDUP_SECRET_NAME);
    }

}
