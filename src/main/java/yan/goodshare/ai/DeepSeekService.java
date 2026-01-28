package yan.goodshare.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class DeepSeekService {

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.url}")
    private String apiUrl;

    @Value("${deepseek.api.model}")
    private String model;

    private final RestClient restClient;
    private final okhttp3.OkHttpClient okHttpClient;

    public DeepSeekService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
        this.okHttpClient = new okhttp3.OkHttpClient.Builder()
                .readTimeout(java.time.Duration.ofSeconds(60))
                .build();
    }

    public void streamChat(String userMessage, String systemMessage, org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemMessage),
                        Map.of("role", "user", "content", userMessage)
                ),
                "stream", true
        );

        try {
            String jsonBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(requestBody);
            
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(okhttp3.RequestBody.create(jsonBody, okhttp3.MediaType.parse("application/json")))
                    .build();

            okHttpClient.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    emitter.completeWithError(e);
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.body().byteStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data:") && line.contains("[DONE]")) {
                                try {
                                    emitter.send("[DONE]");
                                } catch (Exception ignored) {
                                }
                                break;
                            }
                            if (line.startsWith("data:")) {
                                String data = line.substring(5).trim();
                                if (data.isEmpty()) continue;
                                try {
                                    com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(data);
                                    if (node.has("choices") && !node.get("choices").isEmpty()) {
                                        com.fasterxml.jackson.databind.JsonNode delta = node.get("choices").get(0).get("delta");
                                        if (delta.has("content")) {
                                            String content = delta.get("content").asText();
                                            emitter.send(content);
                                        }
                                    }
                                } catch (Exception e) {
                                    // Ignore parse errors for partial chunks
                                }
                            }
                        }
                        emitter.complete();
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                }
            });
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    public String chat(String userMessage) {
        return chat(userMessage, "You are a helpful assistant.");
    }

    public String chat(String userMessage, String systemMessage) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemMessage),
                        Map.of("role", "user", "content", userMessage)
                ),
                "stream", false
        );

        ChatCompletionResponse response = restClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(ChatCompletionResponse.class);

        if (response != null && response.choices() != null && !response.choices().isEmpty()) {
            return response.choices().get(0).message().content();
        }
        return "Sorry, I couldn't get a response from DeepSeek.";
    }

    // DTOs for JSON mapping
    public record ChatCompletionResponse(List<Choice> choices) {}
    public record Choice(Message message) {}
    public record Message(String role, String content) {}
}
