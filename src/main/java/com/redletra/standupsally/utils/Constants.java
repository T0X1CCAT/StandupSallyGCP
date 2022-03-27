package com.redletra.standupsally.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Constants {
    public static final String PROJECT_ID = "standupsally";
    public static final String SLACK_SIGNING_SECRET_NAME = "slackSigningSecret";
    public static final String SLACK_OAUTH_TOKEN_SECRET_NAME = "slackOauthToken";
    public static final String CHANNEL_ID_TO_MEMBER_HANDLES_SECRET_NAME = "channelIdToMemberSlackHandles";
    public static final String LAST_USER_FOR_EACH_CHANNEL_TO_RUN_STANDUP_SECRET_NAME = "channelTolastUserToRunStandup";
    public static final String CHANNEL_TO_SPRINT_START_DATE = "mondaySprintStartDate";

    public static final String SLACK_VERSION_NUMBER = "v0";
    public static final String HMAC_ALGORITHM = "HmacSHA256";

}
