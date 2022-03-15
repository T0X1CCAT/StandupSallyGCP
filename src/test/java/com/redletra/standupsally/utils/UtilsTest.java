package com.redletra.standupsally.utils;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UtilsTest {

    @Test
    void convertChannelToUsersStringToMapTest() {
        String channelToUserString = "channelid1=@tom,@steve;channelid2=@dave,@susy,@chris";
        Map<String, List<String>> channelToUserMap = new Utils().convertChannelToUsersStringToMap(channelToUserString);
        assertEquals(2, channelToUserMap.entrySet().size());
        assertEquals("@tom", channelToUserMap.get("channelid1").get(0));
        assertEquals("@steve", channelToUserMap.get("channelid1").get(1));

        assertEquals("@dave", channelToUserMap.get("channelid2").get(0));
        assertEquals("@susy", channelToUserMap.get("channelid2").get(1));
        assertEquals("@chris", channelToUserMap.get("channelid2").get(2));

        // just one channel
        channelToUserString = "channelid1=@tom,@steve";
        channelToUserMap = new Utils().convertChannelToUsersStringToMap(channelToUserString);
        assertEquals(1, channelToUserMap.entrySet().size());
        assertEquals("@tom", channelToUserMap.get("channelid1").get(0));
    }

    @Test
    void convertChannelIdToUserHandleStringToMapTest() {
        String channelIdToUserHandleString = "channelId1=handleX,channelId2=handleA";
        Map<String, String> channelIdToUserHandleMap = new Utils().convertChannelIdToUserHandleStringToMap(channelIdToUserHandleString);
        assertEquals("handleX", channelIdToUserHandleMap.get("channelId1"));
        assertEquals("handleA", channelIdToUserHandleMap.get("channelId2"));
        assertEquals(2, channelIdToUserHandleMap.size());
    }

    @Test
    void getNextUserHandlesTest() {
        List<String> userHandlesForChannel1 = List.of("@tom", "@steve", "@felix");
        List<String> userHandlesForChannel2 = List.of("@sally", "@jude", "@peter");
        Map<String, List<String>> channelIdToMemberHandlesMap = Map.of("channel1", userHandlesForChannel1,
                "channel2", userHandlesForChannel2);

        // happy path
        Map<String, String> lastUsersToRunStandupInEachChannel = Map.of("channel1", "@steve", "channel2", "@sally");
        Map<String, String> nextUserHandles = new Utils().getNextUserHandles(channelIdToMemberHandlesMap, lastUsersToRunStandupInEachChannel);
        assertEquals("@felix", nextUserHandles.get("channel1"));
        assertEquals("@jude", nextUserHandles.get("channel2"));

        // test when last user in list was last to run
        lastUsersToRunStandupInEachChannel = Map.of("channel1", "@felix", "channel2", "@peter");
        nextUserHandles = new Utils().getNextUserHandles(channelIdToMemberHandlesMap, lastUsersToRunStandupInEachChannel);
        assertEquals("@tom", nextUserHandles.get("channel1"));
        assertEquals("@sally", nextUserHandles.get("channel2"));

        //test when the user has been removed
        lastUsersToRunStandupInEachChannel = Map.of("channel1", "@john", "channel2", "@peter");
        nextUserHandles = new Utils().getNextUserHandles(channelIdToMemberHandlesMap, lastUsersToRunStandupInEachChannel);
        assertEquals("@tom", nextUserHandles.get("channel1"));

        //test when there is no channel in the channel to users map
        lastUsersToRunStandupInEachChannel = Map.of("channel999", "@john", "channel2", "@peter");
        nextUserHandles = new Utils().getNextUserHandles(channelIdToMemberHandlesMap, lastUsersToRunStandupInEachChannel);
        assertEquals(null, nextUserHandles.get("channel1"));

    }

    @Test
    void generateSecretStringFromMapTest() {
        LinkedHashMap<String, String> lastUsersToRunStandupInEachChannelMap = new LinkedHashMap();
        lastUsersToRunStandupInEachChannelMap.put("channel1", "@felix");
        lastUsersToRunStandupInEachChannelMap.put( "channel2", "@peter");
        String secretString = new Utils().generateSecretStringFromChannelIdToLastUserToRunStandupMap(lastUsersToRunStandupInEachChannelMap);
        assertEquals("channel1=@felix,channel2=@peter", secretString);

        lastUsersToRunStandupInEachChannelMap.clear();
        lastUsersToRunStandupInEachChannelMap.put( "channel2", "@peter");
        secretString = new Utils().generateSecretStringFromChannelIdToLastUserToRunStandupMap(lastUsersToRunStandupInEachChannelMap);
        assertEquals("channel2=@peter", secretString);
    }

    @Test
    void generateSecretStringFromChannelIdToUserHandlesListMapTest() {
        List usersChannel1 = List.of("@tom", "@steve", "@henry");
        List usersChannel2 = List.of("@dave", "@tim");
        LinkedHashMap channelIdToUsersListMap = new LinkedHashMap();
        channelIdToUsersListMap.put("channel1", usersChannel1);
        channelIdToUsersListMap.put("channel2", usersChannel2);
        assertEquals("channel1=@tom,@steve,@henry;channel2=@dave,@tim", new Utils().generateSecretStringFromChannelIdToUserHandlesListMap(channelIdToUsersListMap));
    }
}
