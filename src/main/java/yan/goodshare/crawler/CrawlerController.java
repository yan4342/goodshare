package yan.goodshare.crawler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yan.goodshare.entity.Product;

import java.io.IOException;

@RestController
@RequestMapping("/api/crawler")
public class CrawlerController {

    private final CrawlerService crawlerService;

    public CrawlerController(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @PostMapping
    public ResponseEntity<?> crawlProductInfo(@RequestBody String url) {
        try {
            Product product = crawlerService.crawlProductInfo(url);
            return ResponseEntity.ok(product);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Failed to crawl product info: " + e.getMessage());
        }
    }
}
