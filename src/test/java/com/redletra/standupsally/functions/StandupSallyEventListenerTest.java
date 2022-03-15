package com.redletra.standupsally.functions;

import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.secretmanager.v1.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StandupSallyEventListenerTest {

    @Test
    void processMemberJoinedEventTest() {
        SecretManagerServiceClient mockSecretManagerServiceClient = mock(SecretManagerServiceClient.class);
        SecretUtils mockSecretUtils = mock(SecretUtils.class);

        Map<String, List<String>> channelToUserList = new HashMap<String, List<String>>(Map.of("channel1", new ArrayList<String>(List.of("123345", "333333"))));
        SecretVersion versionWhichWillBeDeleted = SecretVersion.newBuilder().build();
        Pair<Map<String, List<String>>, SecretVersion> pair = new Pair(channelToUserList, versionWhichWillBeDeleted);

        when(mockSecretUtils.getChannelIdToUserListMap(mockSecretManagerServiceClient)).thenReturn(pair);

        StandupSallyEventListener standupSallyEventListener = new
                StandupSallyEventListener(mockSecretUtils);

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
    }

    @Test
    void processMemberLeftEventTest() {
        SecretManagerServiceClient mockSecretManagerServiceClient = mock(SecretManagerServiceClient.class);
        SecretUtils mockSecretUtils = mock(SecretUtils.class);

        Map<String, List<String>> channelToUserList = new HashMap<String, List<String>>(Map.of("channel1", new ArrayList<String>(List.of("123345", "333333"))));
        SecretVersion versionWhichWillBeDeleted = SecretVersion.newBuilder().build();
        Pair<Map<String, List<String>>, SecretVersion> pair = new Pair(channelToUserList, versionWhichWillBeDeleted);

        when(mockSecretUtils.getChannelIdToUserListMap(mockSecretManagerServiceClient)).thenReturn(pair);

        StandupSallyEventListener standupSallyEventListener = new
                StandupSallyEventListener(mockSecretUtils);

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
    }

    @Test
    void processValidGCPFunctionFlow() throws Exception {
        SecretUtils mockSecretUtils = mock(SecretUtils.class);
        StandupSallyEventListener standupSallyEventListener = new
                StandupSallyEventListener(mockSecretUtils);

        try (MockedStatic<SecretManagerServiceClient> client = Mockito.mockStatic(SecretManagerServiceClient.class)) {
            SecretManagerServiceClient mockSecretManagerServiceClient = mock(SecretManagerServiceClient.class);
            client.when(SecretManagerServiceClient::create).thenReturn(mockSecretManagerServiceClient);
            AccessSecretVersionResponse signingSecretSecretVersionResponseMock = mock(AccessSecretVersionResponse.class);
            SecretPayload secretPayloadMock = mock(SecretPayload.class);
            when(secretPayloadMock.getData()).thenReturn(ByteString.copyFrom("12345".getBytes()));
            when(signingSecretSecretVersionResponseMock.getPayload()).thenReturn(secretPayloadMock);
            when(mockSecretManagerServiceClient.accessSecretVersion(any(SecretVersionName.class))).thenReturn(signingSecretSecretVersionResponseMock);

            StandupSallyEventListener standupSallyEventListenerSpy = spy(standupSallyEventListener);
            doReturn(true).when(standupSallyEventListenerSpy).validateRequest(any(HttpRequest.class),
                    any(JsonObject.class),
                    anyString());

            HttpRequest httpRequestMock = mock(HttpRequest.class);
            HttpResponse httpResponseMock = mock(HttpResponse.class);

            when(httpRequestMock.getContentType()).thenReturn(Optional.of("application/json"));
            Reader reader = new StringReader("{}");
            BufferedReader br = new BufferedReader(reader);
            when(httpRequestMock.getReader()).thenReturn(br);

            Writer writer = new StringWriter();
            when(httpResponseMock.getWriter()).thenReturn(new BufferedWriter(writer));

            standupSallyEventListenerSpy.service(httpRequestMock, httpResponseMock);

            // attempt is made to validate the request
            verify(standupSallyEventListenerSpy, times(1)).validateRequest(any(HttpRequest.class),
                    any(JsonObject.class),
                    anyString());
            verify(httpResponseMock, times(1)).setStatusCode(200);

        }
    }

    @Test
    void processInValidGCPFunctionFlow() throws Exception {
        SecretUtils mockSecretUtils = mock(SecretUtils.class);
        StandupSallyEventListener standupSallyEventListener = new
                StandupSallyEventListener(mockSecretUtils);

        try (MockedStatic<SecretManagerServiceClient> client = Mockito.mockStatic(SecretManagerServiceClient.class)) {
            SecretManagerServiceClient mockSecretManagerServiceClient = mock(SecretManagerServiceClient.class);
            client.when(SecretManagerServiceClient::create).thenReturn(mockSecretManagerServiceClient);
            AccessSecretVersionResponse signingSecretSecretVersionResponseMock = mock(AccessSecretVersionResponse.class);
            SecretPayload secretPayloadMock = mock(SecretPayload.class);
            when(secretPayloadMock.getData()).thenReturn(ByteString.copyFrom("12345".getBytes()));
            when(signingSecretSecretVersionResponseMock.getPayload()).thenReturn(secretPayloadMock);
            when(mockSecretManagerServiceClient.accessSecretVersion(any(SecretVersionName.class))).thenReturn(signingSecretSecretVersionResponseMock);

            StandupSallyEventListener standupSallyEventListenerSpy = spy(standupSallyEventListener);
            doReturn(false).when(standupSallyEventListenerSpy).validateRequest(any(HttpRequest.class),
                    any(JsonObject.class),
                    anyString());

            HttpRequest httpRequestMock = mock(HttpRequest.class);
            HttpResponse httpResponseMock = mock(HttpResponse.class);


            when(httpRequestMock.getContentType()).thenReturn(Optional.of("application/json"));
            Reader reader = new StringReader("{}");
            BufferedReader br = new BufferedReader(reader);
            when(httpRequestMock.getReader()).thenReturn(br);

            Writer writer = new StringWriter();
            when(httpResponseMock.getWriter()).thenReturn(new BufferedWriter(writer));

            standupSallyEventListenerSpy.service(httpRequestMock, httpResponseMock);

            // attempt is made to validate the request
            verify(standupSallyEventListenerSpy, times(1)).validateRequest(any(HttpRequest.class),
                    any(JsonObject.class),
                    anyString());

            verify(httpResponseMock, times(1)).setStatusCode(400);
        }
    }

    @Test
    void validateRequestTest() {
        SecretUtils mockSecretUtils = mock(SecretUtils.class);
        StandupSallyEventListener standupSallyEventListener = new
                StandupSallyEventListener(mockSecretUtils);

        HttpRequest httpRequestMock = mock(HttpRequest.class);
        when(httpRequestMock.getFirstHeader("X-Slack-Signature"))
                .thenReturn(Optional.of("v0=849e78a96bfc51f106f548b93da581a9625deaae4fffea334db6af6cdb1417ad"));
        when(httpRequestMock.getFirstHeader("X-Slack-Request-Timestamp")).thenReturn(Optional.of("32123123"));
        JsonObject jsonObject = new Gson().fromJson("{}", JsonObject.class);
        boolean result = standupSallyEventListener.validateRequest(httpRequestMock,
                jsonObject,
                "11111111111111111111111111111111");
        assertTrue(result);

    }

    @Test
    void validateRequestNoSlackSignatureTest() {
        SecretUtils mockSecretUtils = mock(SecretUtils.class);
        StandupSallyEventListener standupSallyEventListener = new
                StandupSallyEventListener(mockSecretUtils);

        HttpRequest httpRequestMock = mock(HttpRequest.class);
        when(httpRequestMock.getFirstHeader("X-Slack-Signature"))
                .thenReturn(Optional.of("v0=849e78a96bfc51f106f548b93da581a9625deaae4fffea334db6af6cdb1417ad"));
        JsonObject jsonObject = new Gson().fromJson("{}", JsonObject.class);
        boolean result = standupSallyEventListener.validateRequest(httpRequestMock,
                jsonObject,
                "11111111111111111111111111111111");
        assertFalse(result);

    }

    @Test
    void validateRequestNoSlackTimestampTest() {
        SecretUtils mockSecretUtils = mock(SecretUtils.class);
        StandupSallyEventListener standupSallyEventListener = new
                StandupSallyEventListener(mockSecretUtils);

        HttpRequest httpRequestMock = mock(HttpRequest.class);
        when(httpRequestMock.getFirstHeader("X-Slack-Signature"))
                .thenReturn(Optional.of("v0=849e78a96bfc51f106f548b93da581a9625deaae4fffea334db6af6cdb1417ad"));
        when(httpRequestMock.getFirstHeader("X-Slack-Request-Timestamp")).thenReturn(Optional.ofNullable(null));
        JsonObject jsonObject = new Gson().fromJson("{}", JsonObject.class);
        boolean result = standupSallyEventListener.validateRequest(httpRequestMock,
                jsonObject,
                "11111111111111111111111111111111");
        assertFalse(result);

    }
}
