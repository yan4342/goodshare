package yan.goodshare.crawler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import yan.goodshare.dto.ProductPriceDTO;
import yan.goodshare.entity.Product;

import java.io.IOException;
import java.util.List;

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

    @GetMapping("/compare")
    public ResponseEntity<List<ProductPriceDTO>> comparePrices(@RequestParam String keyword) {
        List<ProductPriceDTO> results = crawlerService.searchProducts(keyword);
        return ResponseEntity.ok(results);
    }
}
