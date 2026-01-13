package yan.goodshare.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import yan.goodshare.product.Product;

import java.io.IOException;

@Service
public class CrawlerService {

    public Product crawlProductInfo(String url) throws IOException {
        // Example implementation for crawling a generic product page.
        // This will need to be adapted for specific websites like Taobao.
        Document doc = Jsoup.connect(url).get();

        String name = doc.select("meta[property=og:title]").attr("content");
        if (name.isEmpty()) {
            name = doc.title();
        }

        String description = doc.select("meta[property=og:description]").attr("content");
        if (description.isEmpty()) {
            description = doc.select("meta[name=description]").attr("content");
        }

        String imageUrl = doc.select("meta[property=og:image]").attr("content");

        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setImageUrl(imageUrl);

        return product;
    }
}
