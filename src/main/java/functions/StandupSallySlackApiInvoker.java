package functions;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class StandupSallySlackApiInvoker {

    Logger logger = LoggerFactory.getLogger(StandupSallySlackApiInvoker.class);

    public void invokeApi(String channelId, String message, String slackOAuthToken) {
        MethodsClient client = Slack.getInstance().methods();
        try {
            // Call the chat.postMessage method using the built-in WebClient

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
    }
}
