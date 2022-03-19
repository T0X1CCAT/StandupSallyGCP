package com.redletra.standupsally.utils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utils {

    /*
       convert channeid1=@tom,@steve;channelid2=@dave,@susy,@chris....
       to a Map<String, List<String>>
     */
    public static Map<String, List<String>> convertChannelToUsersStringToMap(String channelToUsersString) {

        return Arrays.stream(channelToUsersString.split(";"))
            .map(entry -> entry.split("="))
            .collect(Collectors.toMap(entry -> entry[0], entry ->
                 Stream.of(entry[1]
                                .split(",", -1))
                        .collect(Collectors.toList())
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

    /*
     * The sprint start for each channel is just used to as a start date from which we can calculate if today
     * is the ending friday or beginning monday of a sprint. Ideally we could call the Jira API however I can't
     * get through to the corporate jira (need VPN)
     * @param channelToSprintStartDateMap
     * @return
     */
//    public Map<String, Boolean> getIsChannelLastFridayOrFirstMondayOfSprintMap(Map<String, String> channelToSprintStartDateMap,
//                                                                               Map<String, List<String>> channelToUserListMap) {
//        channelToSprintStartDateMap
//    }



}
