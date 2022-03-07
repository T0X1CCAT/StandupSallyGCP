package com.redletra.standupsally.utils;

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
            .peek(p -> System.out.println(p))
            .collect(Collectors.toMap(entry -> entry[0], entry -> {
                return Stream.of(entry[1]
                                .split(",", -1))
                        .collect(Collectors.toList());
            }));
    }

    public static Map<String, String> convertChannelIdToUserHandleStringToMap(String channelIdToUserHandleString ) {
        return Arrays.stream(channelIdToUserHandleString.split(","))
                .map(entry -> entry.split("="))
                .collect(Collectors.toMap(entry -> entry[0], entry -> entry[1]));
    }

    /*
    find the next user to run standup from the last user in each channel to run standup
     */
    public static Map<String, String> getNextUserHandles(Map<String, List<String>> channelIdToMemberHandlesMap,
                                                   Map<String, String> channelIdToUserThatLastRanStandup) {

        Map<String, String> channelIdToUserHandleWhoWillRunStandupMap = new HashMap<>();

        channelIdToUserThatLastRanStandup.forEach((channelId, userHandleToLastRunStandup) -> {
            List<String> userHandlesInChannel = channelIdToMemberHandlesMap.get(channelId);
            if(userHandlesInChannel != null) {
                Optional<Integer> index = Optional.ofNullable(userHandlesInChannel.indexOf(userHandleToLastRunStandup));

                // if the last user isn't in the list anymore then just start the list again
                Integer newUserHandleIndex = index.map(userHandleIndex -> {
                    return (userHandleIndex == userHandlesInChannel.size()-1) ? 0: userHandleIndex.intValue() + 1;
                }).orElse(0);
                String newUserHandle = userHandlesInChannel.get(newUserHandleIndex);
                channelIdToUserHandleWhoWillRunStandupMap.put(channelId, newUserHandle);
            }
        });
        return channelIdToUserHandleWhoWillRunStandupMap;
    }

    /*
    this converts the Map into a string of format we want to save to a secrt
     */
    public static String generateSecretStringFromMap(Map<String, String> channelIdToNextUserHandleToRunStandupMap) {
        return channelIdToNextUserHandleToRunStandupMap.entrySet()
            .stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(","));

    }

}
