package yan.goodshare.crawler;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class TaobaoApiService {

    private final OkHttpClient client = new OkHttpClient();

    @Value("${taobao.api.url}")
    private String apiUrl;

    @Value("${taobao.api.appkey}")
    private String appKey;

    @Value("${taobao.api.secret}")
    private String appSecret;

    public String searchProduct(String keyword) throws IOException {
        // This is a simplified example.
        // A real implementation would involve signing the request with appKey and appSecret.
        String url = apiUrl + "?method=taobao.tbk.dg.material.optional&q=" + keyword + "&adzone_id=123";

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
