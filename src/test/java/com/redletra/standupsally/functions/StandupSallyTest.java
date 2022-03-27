package com.redletra.standupsally.functions;

import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.redletra.standupsally.slack.StandupSallySlackApiInvoker;
import com.redletra.standupsally.utils.SecretUtils;
import org.javatuples.Pair;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

public class StandupSallyTest {


    @Test
    void standupSallyServiceTest() throws Exception {
        HttpRequest httpRequestMock = mock(HttpRequest.class);
        HttpResponse httpResponseMock = mock(HttpResponse.class);
        when(httpResponseMock.getWriter()).thenReturn(new BufferedWriter(new StringWriter()));

        SecretUtils secretUtilsMock = mock(SecretUtils.class);

        try (MockedStatic<SecretManagerServiceClient> client = Mockito.mockStatic(SecretManagerServiceClient.class)) {
            SecretManagerServiceClient mockSecretManagerServiceClient = mock(SecretManagerServiceClient.class);

            when(secretUtilsMock.getSprintStartDate(mockSecretManagerServiceClient)).thenReturn(
                    LocalDate.now().minus(13, ChronoUnit.DAYS)
            );

            when(secretUtilsMock.getSlackToken(mockSecretManagerServiceClient)).thenReturn("slack-token");

            List<String> users = List.of("123456", "343434");
            Map<String, List<String>> channelIdToUserListMap = Map.of("channel1", users);
            SecretVersion secretVersion = mock(SecretVersion.class);

            when(secretUtilsMock.getChannelIdToUserListMap(mockSecretManagerServiceClient))
                    .thenReturn(new Pair(channelIdToUserListMap, secretVersion));

            Map<String, String> userForChannelWhoRanStandup = Map.of("channel1", "2121121221");
            when(secretUtilsMock.getUserForEachChannelWhoLastRanStandup(mockSecretManagerServiceClient))
                    .thenReturn(new Pair(userForChannelWhoRanStandup, secretVersion));

            StandupSallySlackApiInvoker standupSallySlackApiInvokerMock = mock(StandupSallySlackApiInvoker.class);

            StandupSally standupSally = new StandupSally(secretUtilsMock,
                    standupSallySlackApiInvokerMock);

            StandupSally standupSallySpy = spy(standupSally);

            client.when(SecretManagerServiceClient::create).thenReturn(mockSecretManagerServiceClient);

            standupSallySpy.service(httpRequestMock, httpResponseMock);

            verify(standupSallySpy, times(1)).updateLastUserSecret(any(SecretManagerServiceClient.class),
                    anyMap(),
                    any(SecretVersion.class));

            verify(standupSallySlackApiInvokerMock, times(1)).informStandupRunner(anyMap(),
                    anyString());
        }

    }

    @Test
    void standupSallyServiceFirstMondayTest() throws Exception {
        //should not send slack notification on first day of sprint
        HttpRequest httpRequestMock = mock(HttpRequest.class);
        HttpResponse httpResponseMock = mock(HttpResponse.class);
        when(httpResponseMock.getWriter()).thenReturn(new BufferedWriter(new StringWriter()));

        SecretUtils secretUtilsMock = mock(SecretUtils.class);

        try (MockedStatic<SecretManagerServiceClient> client = Mockito.mockStatic(SecretManagerServiceClient.class)) {
            SecretManagerServiceClient mockSecretManagerServiceClient = mock(SecretManagerServiceClient.class);

            when(secretUtilsMock.getSlackToken(mockSecretManagerServiceClient)).thenReturn("slack-token");

            when(secretUtilsMock.getSprintStartDate(mockSecretManagerServiceClient)).thenReturn(
                    LocalDate.now().minus(14, ChronoUnit.DAYS)
            );

            List<String> users = List.of("123456", "343434");
            Map<String, List<String>> channelIdToUserListMap = Map.of("channel1", users);
            SecretVersion secretVersion = mock(SecretVersion.class);

            when(secretUtilsMock.getChannelIdToUserListMap(mockSecretManagerServiceClient))
                    .thenReturn(new Pair(channelIdToUserListMap, secretVersion));

            Map<String, String> userForChannelWhoRanStandup = Map.of("channel1", "2121121221");
            when(secretUtilsMock.getUserForEachChannelWhoLastRanStandup(mockSecretManagerServiceClient))
                    .thenReturn(new Pair(userForChannelWhoRanStandup, secretVersion));

            StandupSallySlackApiInvoker standupSallySlackApiInvokerMock = mock(StandupSallySlackApiInvoker.class);

            StandupSally standupSally = new StandupSally(secretUtilsMock,
                    standupSallySlackApiInvokerMock);

            StandupSally standupSallySpy = spy(standupSally);

            client.when(SecretManagerServiceClient::create).thenReturn(mockSecretManagerServiceClient);

            standupSallySpy.service(httpRequestMock, httpResponseMock);

            verify(standupSallySpy, times(0)).updateLastUserSecret(any(SecretManagerServiceClient.class), anyMap(), any(SecretVersion.class));
            verify(standupSallySlackApiInvokerMock, times(0)).informStandupRunner(anyMap(), anyString());
        }
    }
}
