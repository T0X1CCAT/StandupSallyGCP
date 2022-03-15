package com.redletra.standupsally.utils;

import com.google.cloud.secretmanager.v1.*;
import com.google.protobuf.ByteString;
import org.javatuples.Pair;

import java.util.List;
import java.util.Map;

public class SecretUtils {

    /*
     Retrieve a string like channeid1=@tom,@steve;channelid2=@dave,@susy,@chris.... and convert it to a map of
     Channel Id to Channel User handles, ie Map<String, List<String>>
     Return a Pair containing
     1) the Map and
     2) secret version (in case old secret version needs to be deleted later)
      */
    public Pair<Map<String, List<String>>, SecretVersion> getChannelIdToUserListMap(SecretManagerServiceClient client) {
        SecretVersionName slackChannelIdToMemberHandlesSecretVersionName = SecretVersionName.of(Constants.PROJECT_ID, Constants.CHANNEL_ID_TO_MEMBER_HANDLES_SECRET_NAME, "latest");
        SecretVersion slackChannelIdToMemberHandlesSecretVersion = client.getSecretVersion(slackChannelIdToMemberHandlesSecretVersionName);
        AccessSecretVersionResponse slackChannelIdToMemberHandlesSecretVersionResponse = client.accessSecretVersion(slackChannelIdToMemberHandlesSecretVersionName);
        String slackChannelIdToMemberHandlesCommaSepString = slackChannelIdToMemberHandlesSecretVersionResponse.getPayload().getData().toStringUtf8();
        Map<String, List<String>> channelIdToUserListMap = Utils.convertChannelToUsersStringToMap(slackChannelIdToMemberHandlesCommaSepString);
        return new Pair<>(channelIdToUserListMap, slackChannelIdToMemberHandlesSecretVersion);
    }

    /*
        Create a new secret with supplied value and delete the old secret version
     */
    public void createNewSecretVersionAndDeleteOld(String newSecretVersionValue,
                                                    SecretManagerServiceClient client,
                                                    SecretVersion oldSecretVersionToDelete,
                                                    String secretNameLabel) {

        SecretName secretName = SecretName.of(Constants.PROJECT_ID, secretNameLabel);

        SecretPayload payload =
                SecretPayload.newBuilder()
                        .setData(ByteString.copyFromUtf8(newSecretVersionValue))
                        .build();
        client.addSecretVersion(secretName, payload);

        //delete the old version
        DestroySecretVersionRequest destroySecretVersionRequest = DestroySecretVersionRequest.newBuilder()
                .setName(oldSecretVersionToDelete.getName())
                .build();

        client.destroySecretVersion(destroySecretVersionRequest);
    }

    /*
        get the slack token from secret
         */
    public String getSlackToken(SecretManagerServiceClient client) {
        SecretVersionName slackOauthTokenSecretVersionName = SecretVersionName.of(Constants.PROJECT_ID, Constants.SLACK_OAUTH_TOKEN_SECRET_NAME, "latest");
        AccessSecretVersionResponse slackOauthTokenSecretVersionResponse = client.accessSecretVersion(slackOauthTokenSecretVersionName);
        return slackOauthTokenSecretVersionResponse.getPayload().getData().toStringUtf8();
    }

    public Pair<Map<String,String>, SecretVersion> getUserForEachChannelWhoLastRanStandup(SecretManagerServiceClient client) {
        SecretVersionName lastUserForEachChannelToRunStandupSecretVersionName =
                SecretVersionName.of(Constants.PROJECT_ID, Constants.LAST_USER_FOR_EACH_CHANNEL_TO_RUN_STANDUP_SECRET_NAME, "latest");
        SecretVersion lastUserForEachChannelToRunStandupSecretVersion = client.getSecretVersion(lastUserForEachChannelToRunStandupSecretVersionName);

        AccessSecretVersionResponse lastUserForEachChannelToRunStandupSecretVersionResponse =
                client.accessSecretVersion(lastUserForEachChannelToRunStandupSecretVersionName);
        String lastUserHandleForEachChannelToRunStandup = lastUserForEachChannelToRunStandupSecretVersionResponse.getPayload().getData().toStringUtf8();
        Map<String, String> channelIdToUserWhoLastRanStandupMap = Utils.convertChannelIdToUserHandleStringToMap(lastUserHandleForEachChannelToRunStandup);
        return new Pair<>(channelIdToUserWhoLastRanStandupMap, lastUserForEachChannelToRunStandupSecretVersion);
    }
}
