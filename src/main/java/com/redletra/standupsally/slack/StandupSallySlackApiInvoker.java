package com.redletra.standupsally.slack;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class StandupSallySlackApiInvoker {

    Logger logger = LoggerFactory.getLogger(StandupSallySlackApiInvoker.class);

    /*
      for each channel, let it know who should be running the next standup
     */
    public void informStandupRunner(Map<String, String> channelIdsToNextUserToRunStandup, String slackOAuthToken) {
        MethodsClient client = Slack.getInstance().methods();

        channelIdsToNextUserToRunStandup.forEach((channelId, nextUserIdToRunStandup) -> {
            try {
                // Call the chat.postMessage method using the built-in WebClient
                String message = "Standup Sally says today it's <@" + nextUserIdToRunStandup + ">'s turn to rule standup";
                ChatPostMessageResponse result = client.chatPostMessage(r -> r
                        // The token you used to initialize your app
                        .token(slackOAuthToken)
                        .channel(channelId)
                        .text(message)
                );
                // Print result, which includes information about the message (like TS)
                logger.info("result {}" + result);
            } catch (IOException | SlackApiException e) {
                logger.info("error: {}" + e.getMessage());
            }
        });

    }
}
