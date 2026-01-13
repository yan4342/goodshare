package yan.goodshare.recommendation;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class RecommendationService {

    private final OkHttpClient client = new OkHttpClient();

    @Value("${recommendation.service.url}")
    private String recommendationServiceUrl;

    public String getRecommendations(Long userId) throws IOException {
        String url = recommendationServiceUrl + "/recommendations?user_id=" + userId;

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            return response.body().string();
        }
    }
}
