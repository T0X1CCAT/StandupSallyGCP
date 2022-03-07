package com.redletra.standupsally.functions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redletra.standupsally.utils.InvalidAppRequestException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

import static com.redletra.standupsally.utils.Constants.PROJECT_ID;
import static com.redletra.standupsally.utils.Constants.SLACK_SIGNING_SECRET_NAME;

public class StandupSallyEventListener implements HttpFunction {

    private static final Gson gson = new Gson();


    // get from secrets
    private static final String SLACK_VERSION_NUMBER = "v0";
    private static final String HMAC_ALGORITHM = "SHA-256";

    @Override
    public void service(HttpRequest httpRequest, HttpResponse httpResponse) throws Exception {

        BufferedWriter writer = httpResponse.getWriter();
        BufferedReader reader = httpRequest.getReader();
        try {
            String contentType = httpRequest.getContentType().orElseThrow(() -> new InvalidAppRequestException());
            System.out.println("content type " + contentType);
            //validate
            if ("application/json".equals(contentType)) {

                try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {

                    // get the signing secret used to calculate the signature
                    SecretVersionName signingSecretSecretVersionName = SecretVersionName.of(PROJECT_ID, SLACK_SIGNING_SECRET_NAME, "latest");
                    AccessSecretVersionResponse signingSecretSecretVersionResponse = client.accessSecretVersion(signingSecretSecretVersionName);
                    String slackSigningSecret = signingSecretSecretVersionResponse.getPayload().getData().toStringUtf8();

                    JsonObject body = gson.fromJson(reader, JsonObject.class);
                    if (!validateRequest(httpRequest, body, slackSigningSecret)) {
                        System.out.println("invalid request");
                        throw new InvalidAppRequestException();
                    } else {
                        System.out.println("valid request");
                        processRequest(body, reader, writer, httpResponse);
                    }
                }
            }
        } catch(InvalidAppRequestException iare) {
            System.out.println("invalid content type " + iare.getMessage());
            httpResponse.setStatusCode(400);
            writer.write("invalid request");
        }

    }

    private void processRequest(JsonObject body,
                                BufferedReader reader, BufferedWriter writer,
                                HttpResponse httpResponse) throws IOException {

        // challenge request
        if( body.has("challenge")) {
            System.out.println("has challenge request");
            JsonElement challenge = body.get("challenge");
            httpResponse.setStatusCode(200);
            httpResponse.setContentType("text/plain");
            writer.write(challenge.toString());
        }

        //add user to channel

        //remove user from channel

        //add app to channel
    }

    private boolean validateRequest(HttpRequest httpRequest, JsonObject body, String slackSigningSecret) {

        Optional<String> slackSignature = httpRequest.getFirstHeader("X-Slack-Signature");
        return slackSignature.map(signature -> {
            Optional<String> slackRequestTimestamp = httpRequest.getFirstHeader("X-Slack-Request-Timestamp");
            Optional<Boolean> signatureComparisonResult = slackRequestTimestamp.map(requestTimestamp -> {
                String baseString = SLACK_VERSION_NUMBER + ":" + slackRequestTimestamp + ":" + body.toString();
                SecretKeySpec secretKeySpec = new SecretKeySpec(slackSigningSecret.getBytes(), HMAC_ALGORITHM);
                try {
                    Mac mac = Mac.getInstance(HMAC_ALGORITHM);
                    mac.init(secretKeySpec);
                    String calculatedSignature = Base64.getEncoder().encodeToString(mac.doFinal(baseString.getBytes()));
                    System.out.println("calculatedSignature " + calculatedSignature);
                    System.out.println("signature " + signature);

                    return calculatedSignature.equals(signature);
                } catch (NoSuchAlgorithmException e) {
                    System.out.println("NoSuchAlgorithmException" + e.getMessage());
                    return false;
                } catch (InvalidKeyException e) {
                    System.out.println("InvalidKeyException" + e.getMessage());
                    return false;
                }
            });
            return signatureComparisonResult.get();
        }).orElse(false);

    }
}
