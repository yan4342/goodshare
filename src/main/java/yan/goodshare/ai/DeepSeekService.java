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

    public DeepSeekService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
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
