package com.redletra.standupsally.utils;

import com.google.cloud.functions.HttpRequest;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utils {

    public static final Pattern userFromAppMentionEventPattern =
            Pattern.compile("<.*?>", Pattern.CASE_INSENSITIVE);


    /*
       convert channeid1=@tom,@steve;channelid2=@dave,@susy,@chris....
       to a Map<String, List<String>>
     */
    public static Map<String, List<String>> convertChannelToUsersStringToMap(String channelToUsersString) {

        return Arrays.stream(channelToUsersString.split(";"))
            .map(entry -> entry.split("="))
            .collect(Collectors.toMap(entry -> entry[0], entry ->
                   entry.length > 1 ?
                        Stream.of(entry[1]
                                        .split(",", -1))
                                .collect(Collectors.toList()) :
                        new ArrayList<>()
            ));
    }

    public static Map<String, String> convertChannelIdToUserHandleStringToMap(String channelIdToUserHandleString ) {
        return Arrays.stream(channelIdToUserHandleString.split(","))
                .map(entry -> entry.split("="))
                .collect(Collectors.toMap(entry -> entry[0], entry -> entry[1]));
    }

    /*
    find the next user to run standup from the last user in each channel to run standup
    @Return Map of channelId to the next user to run standup
     */
    public static Map<String, String> getNextUserHandles(Map<String, List<String>> channelIdToMemberHandlesMap,
                                                   Map<String, String> channelIdToUserThatLastRanStandup) {

        Map<String, String> channelIdToUserHandleWhoWillRunStandupMap = new HashMap<>();

        channelIdToUserThatLastRanStandup.forEach((channelId, userHandleToLastRunStandup) -> {
            List<String> userHandlesInChannel = channelIdToMemberHandlesMap.get(channelId);
            if(userHandlesInChannel != null) {
                int userHandleIndex = userHandlesInChannel.indexOf(userHandleToLastRunStandup);

                int newUserHandleIndex = 0;
                // if the last user isn't in the list anymore then just start the list again
                if (userHandleIndex != -1) {
                    newUserHandleIndex = (userHandleIndex == userHandlesInChannel.size()-1) ? 0: userHandleIndex + 1;
                }

                String newUserHandle = userHandlesInChannel.get(newUserHandleIndex);
                channelIdToUserHandleWhoWillRunStandupMap.put(channelId, newUserHandle);
            }
        });
        return channelIdToUserHandleWhoWillRunStandupMap;
    }

    /*
        This converts the ChannelIdToLastUserToRunStandupMap into a string of format we want to save to a secret
     */
    public static String generateSecretStringFromChannelIdToLastUserToRunStandupMap(Map<String, String> channelIdToNextUserHandleToRunStandupMap) {
        return channelIdToNextUserHandleToRunStandupMap.entrySet()
            .stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(","));

    }

    /*
    This converts the ChannelIdToUserHandlesListMap into a string of format we want to save to a secret
 */
    public static String generateSecretStringFromChannelIdToUserHandlesListMap(Map<String, List<String>> channelIdToUserHandlesListMap) {
        return channelIdToUserHandlesListMap.entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + (entry.getValue().stream().map(user -> user ).collect(Collectors.joining(","))))
                .collect(Collectors.joining(";"));

    }

    public static boolean notFirstMondayOfSprint (LocalDate mondaySprintLocalDate, LocalDate today) {
        long daysBetween = ChronoUnit.DAYS.between(mondaySprintLocalDate, today );
        return daysBetween % 14 != 0;
    }

    public static Optional<String> getUserFromAppMentionEvent(String appMentionEventContent) {
        Matcher matcher = userFromAppMentionEventPattern.matcher(appMentionEventContent);
        // standup sally user match
        boolean foundStandupSallyUser = matcher.find();
        if(foundStandupSallyUser) {
            String standupSallyUser = matcher.group();

            // user being mentioned
            boolean foundUserMentioned = matcher.find();
            if(foundUserMentioned) {
                String userWithAngleBrackets = matcher.group();

                // note if we add Standup Sally to a channel, it will trigger an add user
                // event, where the standup sally user is the user to be added. Obviously we
                // don't want this, and if it happens just ignore and return an empty
                if(standupSallyUser.equals(userWithAngleBrackets)) {
                    return Optional.empty();
                } else {
                    userWithAngleBrackets = StringUtils.remove(userWithAngleBrackets, "<@");
                    return Optional.of(StringUtils.remove(userWithAngleBrackets, ">"));
                }
            }
        }
        throw new InvalidAppRequestException("could not extract user being mentioned");
    }

    public static String generateSlackUsersString(List<String> slackUsersForChannelArray) {
        return slackUsersForChannelArray.stream().map(user -> "<@" + user +">").
                collect(Collectors.joining(","));
    }

    public static void debugHeaders(HttpRequest httpRequest) {
        for (Map.Entry<String, List<String>> header :httpRequest.getHeaders().entrySet()) {
            System.out.println("key: " + header.getKey() + header.getValue().stream().collect(Collectors.joining(",")));
        }
    }

    public static Optional<String> getSallyUserFromAppMentionEvent(String appMentionContent) {
        Matcher matcher = userFromAppMentionEventPattern.matcher(appMentionContent);
        // standup sally user match
        return matcher.find() ?
                Optional.of(matcher.group()) :
                Optional.empty();
    }

    public static Optional<String> getSlackRetryNumHeader(HttpRequest httpRequest) {
        return httpRequest.getFirstHeader("x-slack-retry-num").or(() -> {
            return httpRequest.getFirstHeader("X-Slack-Retry-Num");
        });
    }

    public static Optional<String> getSlackSignatureHeader(HttpRequest httpRequest) {
        return httpRequest.getFirstHeader("x-slack-signature").or(() -> {
            return httpRequest.getFirstHeader("X-Slack-Signature");
        });
    }

    public static Optional<String> getSlackTimestampHeader(HttpRequest httpRequest) {
        return httpRequest.getFirstHeader("x-slack-request-timestamp").or(() -> {
            return httpRequest.getFirstHeader("X-Slack-Request-Timestamp");
        });
    }


}

