package yan.goodshare.price;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import yan.goodshare.crawler.CrawlerService;
import yan.goodshare.dto.ProductPriceDTO;

import java.util.List;

@RestController
@RequestMapping("/api/prices")
public class PriceController {

    private final CrawlerService crawlerService;

    public PriceController(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductPriceDTO>> searchPrices(@RequestParam String keyword) {
        return ResponseEntity.ok(crawlerService.searchProducts(keyword));
    }

    @GetMapping("/history")
    public ResponseEntity<List<yan.goodshare.dto.ProductHistoryDTO>> getPriceHistory(@RequestParam String keyword) {
        return ResponseEntity.ok(crawlerService.getProductHistory(keyword));
    }
}
