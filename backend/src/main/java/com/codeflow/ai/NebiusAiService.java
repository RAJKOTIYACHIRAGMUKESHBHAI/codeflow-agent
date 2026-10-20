
package com.codeflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class NebiusAiService {

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public NebiusAiService(
            @Value("${nebius.api.base-url}") String baseUrl,
            @Value("${nebius.api.key}") String apiKey) {

        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public String generateResponse(String prompt)
            throws Exception {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("NEBIUS_API_KEY is not configured");
        }

        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException(
                    "Prompt must not be empty");
        }

        Map<String, Object> body = Map.of(
                "model", "nvidia/Nemotron-3_5-Lightning",
                "messages", new Object[]{
                        Map.of("role", "user", "content", prompt)
                },
                "max_tokens", 512,
                "temperature", 0,
                "chat_template_kwargs",
                Map.of("enable_thinking", false)
        );

        String requestBody =
                objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        requestBody))
                .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Nebius API returned HTTP "
                            + response.statusCode());
        }

        JsonNode root =
                objectMapper.readTree(response.body());

        JsonNode content = root.path("choices")
                .path(0).path("message").path("content");

        if (!content.isTextual()
                || content.asText().isBlank()) {
            throw new IllegalStateException(
                    "Nebius returned an empty response");
        }

        return content.asText();
    }
}
