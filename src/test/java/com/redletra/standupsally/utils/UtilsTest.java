package com.redletra.standupsally.utils;

import com.google.cloud.functions.HttpRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.redletra.standupsally.utils.Utils.notFirstMondayOfSprint;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        // just one user
        channelToUserString = "channel1=@tom,@charles;channel2=";
        channelToUserMap = new Utils().convertChannelToUsersStringToMap(channelToUserString);
        assertEquals(2, channelToUserMap.entrySet().size());
        assertEquals("@tom", channelToUserMap.get("channel1").get(0));
        assertTrue(channelToUserMap.get("channel2").isEmpty());
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
        Map<String, String> nextUserHandles = Utils.getNextUserHandles(channelIdToMemberHandlesMap, lastUsersToRunStandupInEachChannel);
        assertEquals("@felix", nextUserHandles.get("channel1"));
        assertEquals("@jude", nextUserHandles.get("channel2"));

        // test when last user in list was last to run
        lastUsersToRunStandupInEachChannel = Map.of("channel1", "@felix", "channel2", "@peter");
        nextUserHandles = Utils.getNextUserHandles(channelIdToMemberHandlesMap, lastUsersToRunStandupInEachChannel);
        assertEquals("@tom", nextUserHandles.get("channel1"));
        assertEquals("@sally", nextUserHandles.get("channel2"));

        //test when the user has been removed
        lastUsersToRunStandupInEachChannel = Map.of("channel1", "@john", "channel2", "@peter");
        nextUserHandles = Utils.getNextUserHandles(channelIdToMemberHandlesMap, lastUsersToRunStandupInEachChannel);
        assertEquals("@tom", nextUserHandles.get("channel1"));

        //test when there is no channel in the channel to users map
        lastUsersToRunStandupInEachChannel = Map.of("channel999", "@john", "channel2", "@peter");
        nextUserHandles = Utils.getNextUserHandles(channelIdToMemberHandlesMap, lastUsersToRunStandupInEachChannel);
        assertEquals(null, nextUserHandles.get("channel1"));

    }

    @Test
    void notFirstMondayOfSprintTest() {
        LocalDate today = LocalDate.parse("27/03/2022", DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        LocalDate mondaySprintStart = LocalDate.parse("14/03/2022", DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertTrue(notFirstMondayOfSprint(mondaySprintStart, today));

        today = LocalDate.parse("29/03/2022", DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        mondaySprintStart = LocalDate.parse("14/03/2022", DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertTrue(notFirstMondayOfSprint(mondaySprintStart, today));

        today = LocalDate.parse("28/03/2022", DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        mondaySprintStart = LocalDate.parse("14/03/2022", DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertFalse(notFirstMondayOfSprint(mondaySprintStart, today));

        today = LocalDate.parse("11/04/2022", DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        mondaySprintStart = LocalDate.parse("14/03/2022", DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertFalse(notFirstMondayOfSprint(mondaySprintStart, today));
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
        assertEquals("channel1=@tom,@steve,@henry;channel2=@dave,@tim", Utils.generateSecretStringFromChannelIdToUserHandlesListMap(channelIdToUsersListMap));
    }

    @Test
    void appMentionEventSuccessfulTest() {
        Assertions.assertEquals("U01LMQ1KAFL",
                Utils.getUserFromAppMentionEvent("<@U032VM4S54Z> remove <@U01LMQ1KAFL>").get());
    }

    @Test
    void appMentionEventFailedTest() {
        Assertions.assertThrows(InvalidAppRequestException.class, () ->
            Utils.getUserFromAppMentionEvent("<@U032VM4S54Z> remove ").get());
    }

    @Test
    void generateSlackUsersStringTest() {
        List users = List.of("12345", "234567", "78903");
        assertEquals("<@12345>,<@234567>,<@78903>", Utils.generateSlackUsersString(users));
    }

    @Test
    void getSlackRetryNumHeaderTest() {
        HttpRequest mock = mock(HttpRequest.class);
        when(mock.getFirstHeader("x-slack-retry-num")).thenReturn(Optional.empty());
        Assertions.assertEquals(Optional.empty(), Utils.getSlackRetryNumHeader(mock));

        when(mock.getFirstHeader("X-Slack-Retry-Num")).thenReturn(Optional.empty());
        Assertions.assertEquals(Optional.empty(), Utils.getSlackRetryNumHeader(mock));

        when(mock.getFirstHeader("x-slack-retry-num")).thenReturn(Optional.of("retryNum"));
        Assertions.assertEquals(Optional.of("retryNum"), Utils.getSlackRetryNumHeader(mock));

        when(mock.getFirstHeader("X-Slack-Retry-Num")).thenReturn(Optional.of("retryNum"));
        Assertions.assertEquals(Optional.of("retryNum"), Utils.getSlackRetryNumHeader(mock));
    }

}
