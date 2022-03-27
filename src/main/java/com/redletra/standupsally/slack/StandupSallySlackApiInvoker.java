package com.redletra.standupsally.slack;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import org.eclipse.jetty.util.UrlEncoded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.*;

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

    public void appMentionActionFeedback(String message,
                                         String slackOAuthToken,
                                         String channelId) {
        MethodsClient client = Slack.getInstance().methods();

        try {
            ChatPostMessageResponse result = client.chatPostMessage(r -> r
                    // The token you used to initialize your app
                    .token(slackOAuthToken)
                    .channel(channelId)
                    .text(message)
            );
        } catch (IOException | SlackApiException e) {
            logger.info("error: {}" + e.getMessage());
        }

    }

    public List<String> getSlackUsersForChannel (String channelId, String slackAuthToken) {

        URL url = null;
        try {
            url = new URL("https://slack.com/api/conversations.members?channel=" + channelId);

            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            con.setRequestProperty("Authorization", "Bearer " + slackAuthToken);
            int status = con.getResponseCode();
            if (200 == status) {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer content = new StringBuffer();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                JsonObject jsonObject = new Gson().fromJson(content.toString(), JsonObject.class);
                JsonArray members = jsonObject.getAsJsonArray("members");
                List <String> slackUsersForChannel = new ArrayList<>();
                for (JsonElement userElement : members) {
                    slackUsersForChannel.add(userElement.getAsString());
                }
                return slackUsersForChannel;
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (ProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

}
