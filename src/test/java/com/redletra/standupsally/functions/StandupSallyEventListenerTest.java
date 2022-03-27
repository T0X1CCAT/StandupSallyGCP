package com.redletra.standupsally.functions;

import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.secretmanager.v1.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import com.redletra.standupsally.slack.StandupSallySlackApiInvoker;
import com.redletra.standupsally.utils.InvalidAppRequestException;
import com.redletra.standupsally.utils.SecretUtils;
import org.javatuples.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.*;
import java.util.*;

import static com.redletra.standupsally.utils.Constants.CHANNEL_ID_TO_MEMBER_HANDLES_SECRET_NAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StandupSallyEventListenerTest {

    @Test
    void processMemberJoinedEventTest() {
        SecretManagerServiceClient mockSecretManagerServiceClient = mock(SecretManagerServiceClient.class);
        SecretUtils mockSecretUtils = mock(SecretUtils.class);

        StandupSallySlackApiInvoker standupSallySlackApiInvokerMock = mock(StandupSallySlackApiInvoker.class);
        Map<String, List<String>> channelToUserList = new HashMap<String, List<String>>(Map.of("channel1", new ArrayList<String>(List.of("123345", "333333"))));
        SecretVersion versionWhichWillBeDeleted = SecretVersion.newBuilder().build();
        Pair<Map<String, List<String>>, SecretVersion> pair = new Pair(channelToUserList, versionWhichWillBeDeleted);

        when(mockSecretUtils.getChannelIdToUserListMap(mockSecretManagerServiceClient)).thenReturn(pair);

        when(mockSecretUtils.getSlackToken(mockSecretManagerServiceClient)).thenReturn("secret");

        StandupSallyEventListener standupSallyEventListener = new
                StandupSallyEventListener(mockSecretUtils, standupSallySlackApiInvokerMock);

        String memberJoinedEvent = "{\"user\": \"W010SG2LC7K\",\"channel\": \"channel1\"}";
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(memberJoinedEvent, JsonObject.class);

        // test method
        standupSallyEventListener.processMemberJoinedEvent(jsonObject, mockSecretManagerServiceClient);

        String newSecretValue = "channel1=123345,333333,W010SG2LC7K";
        verify(mockSecretUtils, times(1)).createNewSecretVersionAndDeleteOld(
                newSecretValue,
                mockSecretManagerServiceClient,
                versionWhichWillBeDeleted,
                CHANNEL_ID_TO_MEMBER_HANDLES_SECRET_NAME);

        verify(standupSallySlackApiInvokerMock, times(1)).appMentionActionFeedback(
                anyString(),
                anyString(),
                anyString());
    }

    @Test
    void processAppMentionAddEventTest() throws FileNotFoundException {

        SecretManagerServiceClient mockSecretManagerServiceClient = mock(SecretManagerServiceClient.class);
        StandupSallySlackApiInvoker standupSallySlackApiInvokerMock = mock(StandupSallySlackApiInvoker.class);

        SecretUtils secretUtilsMock = mock(SecretUtils.class);
        when(secretUtilsMock.getSlackToken(mockSecretManagerServiceClient)).thenReturn("slack-token");
        List<String> users = new ArrayList<>();
        users.add("123456");
        users.add("343434");
        Map<String, List<String>> channelIdToUserListMap = new HashMap<>();
        channelIdToUserListMap.put("channel1", users);
        SecretVersion secretVersion = mock(SecretVersion.class);
        when(secretUtilsMock.getChannelIdToUserListMap(mockSecretManagerServiceClient))
                .thenReturn(new Pair(channelIdToUserListMap, secretVersion));

        StandupSallyEventListener standupSallyEventListener = new
                StandupSallyEventListener(secretUtilsMock, standupSallySlackApiInvokerMock);
        StandupSallyEventListener standupSallyEventListenerSpy = spy(standupSallyEventListener);

        String file ="src/test/resources/fixtures/app-mention-add-user.json";

        BufferedReader reader = new BufferedReader(new FileReader(file));

        JsonObject appMentionEvent = new Gson().fromJson(reader, JsonObject.class);

        standupSallyEventListenerSpy.processAppMentionEvent(appMentionEvent, mockSecretManagerServiceClient);

        verify(standupSallyEventListenerSpy, times(1)).addUserToChannel(anyString(),
                anyString(),
                any(SecretManagerServiceClient.class));

    }

    @Test
    void processAppMentionAddEvent_do_not_add_sally_userTest() throws FileNotFoundException {

        SecretManagerServiceClient mockSecretManagerServiceClient = mock(SecretManagerServiceClient.class);
        StandupSallySlackApiInvoker standupSallySlackApiInvokerMock = mock(StandupSallySlackApiInvoker.class);

        SecretUtils secretUtilsMock = mock(SecretUtils.class);
        List<String> users = new ArrayList<>();
        users.add("123456");
        users.add("343434");
        Map<String, List<String>> channelIdToUserListMap = new HashMap<>();
        channelIdToUserListMap.put("channel1", users);
        SecretVersion secretVersion = mock(SecretVersion.class);

        StandupSallyEventListener standupSallyEventListener = new
                StandupSallyEventListener(secretUtilsMock, standupSallySlackApiInvokerMock);
        StandupSallyEventListener standupSallyEventListenerSpy = spy(standupSallyEventListener);

        String file ="src/test/resources/fixtures/app-mention-add-sally-user.json";

        BufferedReader reader = new BufferedReader(new FileReader(file));

        JsonObject appMentionEvent = new Gson().fromJson(reader, JsonObject.class);

        standupSallyEventListenerSpy.processAppMentionEvent(appMentionEvent, mockSecretManagerServiceClient);

        verify(standupSallyEventListenerSpy, times(0)).addUserToChannel(anyString(),
                anyString(),
                any(SecretManagerServiceClient.class));

    }

    @Test
    void processAppMentionRemoveEventTest() throws FileNotFoundException {

        SecretManagerServiceClient mockSecretManagerServiceClient = mock(SecretManagerServiceClient.class);
        StandupSallySlackApiInvoker standupSallySlackApiInvokerMock = mock(StandupSallySlackApiInvoker.class);

        SecretUtils secretUtilsMock = mock(SecretUtils.class);
        when(secretUtilsMock.getSlackToken(mockSecretManagerServiceClient)).thenReturn("slack-token");
        List<String> users = new ArrayList<>();
        users.add("123456");
        users.add("343434");
        Map<String, List<String>> channelIdToUserListMap = new HashMap<>();
        channelIdToUserListMap.put("channel1", users);
        SecretVersion secretVersion = mock(SecretVersion.class);
        when(secretUtilsMock.getChannelIdToUserListMap(mockSecretManagerServiceClient))
                .thenReturn(new Pair(channelIdToUserListMap, secretVersion));

        StandupSallyEventListener standupSallyEventListener = new
                StandupSallyEventListener(secretUtilsMock, standupSallySlackApiInvokerMock);
        StandupSallyEventListener standupSallyEventListenerSpy = spy(standupSallyEventListener);

        String file ="src/test/resources/fixtures/app-mention-remove-user.json";

        BufferedReader reader = new BufferedReader(new FileReader(file));

        JsonObject appMentionEvent = new Gson().fromJson(reader, JsonObject.class);

        standupSallyEventListenerSpy.processAppMentionEvent(appMentionEvent, mockSecretManagerServiceClient);

        verify(standupSallyEventListenerSpy, times(1)).removeUserFromChannel(anyString(),
                anyString(),
                any(SecretManagerServiceClient.class));

    }

    @Test
    void processAppMentionAddUsEventTest() throws FileNotFoundException {
        SecretManagerServiceClient mockSecretManagerServiceClient = mock(SecretManagerServiceClient.class);
        StandupSallySlackApiInvoker standupSallySlackApiInvokerMock = mock(StandupSallySlackApiInvoker.class);

        List usersInChannelFromApi = new ArrayList();
        usersInChannelFromApi.add("<@123456789>");
        usersInChannelFromApi.add("<@223456789>");
        usersInChannelFromApi.add("<@323456789>");

        when(standupSallySlackApiInvokerMock.getSlackUsersForChannel(anyString(),
                anyString())).thenReturn(usersInChannelFromApi);

        SecretUtils secretUtilsMock = mock(SecretUtils.class);
        when(secretUtilsMock.getSlackToken(mockSecretManagerServiceClient)).thenReturn("slack-token");

        List<String> users = new ArrayList<>();
        users.add("123456");
        users.add("343434");
        Map<String, List<String>> channelIdToUserListMap = new HashMap<>();
        channelIdToUserListMap.put("channel1", users);
        SecretVersion secretVersion = mock(SecretVersion.class);
        when(secretUtilsMock.getChannelIdToUserListMap(mockSecretManagerServiceClient))
                .thenReturn(new Pair(channelIdToUserListMap, secretVersion));


        when(secretUtilsMock.getUserForEachChannelWhoLastRanStandup(mockSecretManagerServiceClient)).
                thenReturn(new Pair(new HashMap<>(), secretVersion));

        StandupSallyEventListener standupSallyEventListener = new
                StandupSallyEventListener(secretUtilsMock, standupSallySlackApiInvokerMock);
        StandupSallyEventListener standupSallyEventListenerSpy = spy(standupSallyEventListener);

        String file ="src/test/resources/fixtures/app-mention-add-us.json";

        BufferedReader reader = new BufferedReader(new FileReader(file));

        JsonObject appMentionEvent = new Gson().fromJson(reader, JsonObject.class);

        standupSallyEventListenerSpy.processAppMentionEvent(appMentionEvent, mockSecretManagerServiceClient);

        verify(standupSallyEventListenerSpy, times(1)).addChannelUsersToSally(anyString(),
                any(SecretManagerServiceClient.class),
                anyString());

    }

    @Test
    void processChallengeSlackEvent() throws Exception {
        SecretUtils mockSecretUtils = mock(SecretUtils.class);

        StandupSallySlackApiInvoker standupSallySlackApiInvokerMock = mock(StandupSallySlackApiInvoker.class);

        StandupSallyEventListener standupSallyEventListener = new
                StandupSallyEventListener(mockSecretUtils, standupSallySlackApiInvokerMock);

        HttpRequest httpRequestMock = mock(HttpRequest.class);
        HttpResponse httpResponseMock = mock(HttpResponse.class);

        Reader reader = new StringReader("{\"challenge\": \"asDQiWVn3xDllsXB8U3G1V5VImFPwNTTPGmFxXx2WOjXI85BNbAX\"}");
        BufferedReader br = new BufferedReader(reader);
        when(httpRequestMock.getReader()).thenReturn(br);

        Writer writer = new StringWriter();
        BufferedWriter bwriterMock = mock(BufferedWriter.class);
        doNothing().when(bwriterMock).write(anyString());
        when(httpResponseMock.getWriter()).thenReturn(bwriterMock);

        standupSallyEventListener.service(httpRequestMock, httpResponseMock);

        verify(bwriterMock, times(1)).write(anyString());
        verify(httpResponseMock, times(1)).setContentType("text/plain");


    }

    @Test
    void processMemberLeftEventTest() {
        SecretManagerServiceClient mockSecretManagerServiceClient = mock(SecretManagerServiceClient.class);
        SecretUtils mockSecretUtils = mock(SecretUtils.class);

        StandupSallySlackApiInvoker standupSallySlackApiInvokerMock = mock(StandupSallySlackApiInvoker.class);

        Map<String, List<String>> channelToUserList = new HashMap<String, List<String>>(Map.of("channel1", new ArrayList<String>(List.of("123345", "333333"))));
        SecretVersion versionWhichWillBeDeleted = SecretVersion.newBuilder().build();
        Pair<Map<String, List<String>>, SecretVersion> pair = new Pair(channelToUserList, versionWhichWillBeDeleted);

        when(mockSecretUtils.getChannelIdToUserListMap(mockSecretManagerServiceClient)).thenReturn(pair);
        when(mockSecretUtils.getSlackToken(mockSecretManagerServiceClient)).thenReturn("secret");

        StandupSallyEventListener standupSallyEventListener = new
                StandupSallyEventListener(mockSecretUtils, standupSallySlackApiInvokerMock);

        String memberLeftEvent = "{\"user\": \"123345\",\"channel\": \"channel1\"}";
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(memberLeftEvent, JsonObject.class);

        standupSallyEventListener.processMemberLeftEvent(jsonObject, mockSecretManagerServiceClient);

        String newSecretValue = "channel1=333333";
        verify(mockSecretUtils, times(1)).createNewSecretVersionAndDeleteOld(
                newSecretValue,
                mockSecretManagerServiceClient,
                versionWhichWillBeDeleted,
                CHANNEL_ID_TO_MEMBER_HANDLES_SECRET_NAME);

        verify(standupSallySlackApiInvokerMock, times(1)).appMentionActionFeedback(
                anyString(),
                anyString(),
                anyString());
    }

    @Test
    void processValidGCPFunctionFlow() throws Exception {
        SecretUtils mockSecretUtils = mock(SecretUtils.class);

        StandupSallySlackApiInvoker standupSallySlackApiInvokerMock = mock(StandupSallySlackApiInvoker.class);

        StandupSallyEventListener standupSallyEventListener = new
                StandupSallyEventListener(mockSecretUtils, standupSallySlackApiInvokerMock);

        try (MockedStatic<SecretManagerServiceClient> client = Mockito.mockStatic(SecretManagerServiceClient.class)) {
            SecretManagerServiceClient mockSecretManagerServiceClient = mock(SecretManagerServiceClient.class);
            client.when(SecretManagerServiceClient::create).thenReturn(mockSecretManagerServiceClient);
            AccessSecretVersionResponse signingSecretSecretVersionResponseMock = mock(AccessSecretVersionResponse.class);
            SecretPayload secretPayloadMock = mock(SecretPayload.class);
            when(secretPayloadMock.getData()).thenReturn(ByteString.copyFrom("12345".getBytes()));
            when(signingSecretSecretVersionResponseMock.getPayload()).thenReturn(secretPayloadMock);
            when(mockSecretManagerServiceClient.accessSecretVersion(any(SecretVersionName.class))).thenReturn(signingSecretSecretVersionResponseMock);

            StandupSallyEventListener standupSallyEventListenerSpy = spy(standupSallyEventListener);
            doReturn(true).when(standupSallyEventListenerSpy).validateRequest(any(JsonObject.class),
                    anyString(),
                    anyString(),
                    anyString());

            HttpRequest httpRequestMock = mock(HttpRequest.class);
            HttpResponse httpResponseMock = mock(HttpResponse.class);

            when(httpRequestMock.getFirstHeader("x-slack-retry-num")).thenReturn(Optional.empty());
            when(httpRequestMock.getFirstHeader("x-slack-signature")).thenReturn(Optional.of("slack-sig"));
            when(httpRequestMock.getFirstHeader("x-slack-request-timestamp")).thenReturn(Optional.of("121211212"));

            Reader reader = new StringReader("{}");
            BufferedReader br = new BufferedReader(reader);
            when(httpRequestMock.getReader()).thenReturn(br);

            Writer writer = new StringWriter();
            when(httpResponseMock.getWriter()).thenReturn(new BufferedWriter(writer));

            standupSallyEventListenerSpy.service(httpRequestMock, httpResponseMock);

            // attempt is made to validate the request
            verify(standupSallyEventListenerSpy, times(1)).validateRequest(any(JsonObject.class),
                    anyString(),
                    anyString(),
                    anyString());

            verify(httpResponseMock, times(1)).setStatusCode(200);

        }
    }

    @Test
    void processInValidGCPFunctionFlow() throws Exception {
        SecretUtils mockSecretUtils = mock(SecretUtils.class);
        StandupSallySlackApiInvoker standupSallySlackApiInvokerMock = mock(StandupSallySlackApiInvoker.class);

        StandupSallyEventListener standupSallyEventListener = new
                StandupSallyEventListener(mockSecretUtils, standupSallySlackApiInvokerMock);

        try (MockedStatic<SecretManagerServiceClient> client = Mockito.mockStatic(SecretManagerServiceClient.class)) {
            SecretManagerServiceClient mockSecretManagerServiceClient = mock(SecretManagerServiceClient.class);
            client.when(SecretManagerServiceClient::create).thenReturn(mockSecretManagerServiceClient);
            AccessSecretVersionResponse signingSecretSecretVersionResponseMock = mock(AccessSecretVersionResponse.class);
            SecretPayload secretPayloadMock = mock(SecretPayload.class);
            when(secretPayloadMock.getData()).thenReturn(ByteString.copyFrom("12345".getBytes()));
            when(signingSecretSecretVersionResponseMock.getPayload()).thenReturn(secretPayloadMock);
            when(mockSecretManagerServiceClient.accessSecretVersion(any(SecretVersionName.class))).thenReturn(signingSecretSecretVersionResponseMock);

            StandupSallyEventListener standupSallyEventListenerSpy = spy(standupSallyEventListener);
            doReturn(false).when(standupSallyEventListenerSpy).validateRequest(any(JsonObject.class),
                    anyString(),
                    anyString(),
                    anyString());

            HttpRequest httpRequestMock = mock(HttpRequest.class);
            HttpResponse httpResponseMock = mock(HttpResponse.class);

            when(httpRequestMock.getFirstHeader("x-slack-retry-num")).thenReturn(Optional.empty());
            when(httpRequestMock.getFirstHeader("x-slack-signature")).thenReturn(Optional.of("slack-sig"));
            when(httpRequestMock.getFirstHeader("x-slack-request-timestamp")).thenReturn(Optional.of("121211212"));
             Reader reader = new StringReader("{}");
            BufferedReader br = new BufferedReader(reader);
            when(httpRequestMock.getReader()).thenReturn(br);

            Writer writer = new StringWriter();
            when(httpResponseMock.getWriter()).thenReturn(new BufferedWriter(writer));

            try {
                standupSallyEventListenerSpy.service(httpRequestMock, httpResponseMock);
            }catch (InvalidAppRequestException in){
                assertNotNull(in);
            }
            // attempt is made to validate the request
            verify(standupSallyEventListenerSpy, times(1)).validateRequest(any(JsonObject.class),
                    anyString(),
                    anyString(),
                    anyString());


        }
    }

    @Test
    void validateRequestTest() {
        SecretUtils mockSecretUtils = mock(SecretUtils.class);
        StandupSallySlackApiInvoker standupSallySlackApiInvokerMock = mock(StandupSallySlackApiInvoker.class);
        StandupSallyEventListener standupSallyEventListener = new
                StandupSallyEventListener(mockSecretUtils, standupSallySlackApiInvokerMock);
        JsonObject jsonObject = new Gson().fromJson("{}", JsonObject.class);
        boolean result = new StandupSallyEventListener().validateRequest(
                jsonObject,
                "11111111111111111111111111111111",
                "v0=849e78a96bfc51f106f548b93da581a9625deaae4fffea334db6af6cdb1417ad",
                "32123123");
        assertTrue(result);

    }

//    @Test
//    void addChannelUsersToSallyTest() {
//       todo
//    }
//
//    @Test
//    void removeUserFromChannelTest() {
//       todo
//    }
//
//    @Test
//    void addUserToChannelTest() {
//      todo
//    }

}
