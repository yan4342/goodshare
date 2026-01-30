package yan.goodshare.crawler.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import yan.goodshare.crawler.service.CrawlerService;
import yan.goodshare.crawler.dto.ProductPriceDTO;
import yan.goodshare.crawler.dto.ProductHistoryDTO;

import java.util.List;

@RestController
@RequestMapping("/api/prices")
public class PriceController {

    private final CrawlerService crawlerService;

    public PriceController(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductPriceDTO>> searchPrices(
            @RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        return ResponseEntity.ok(crawlerService.searchProducts(keyword, refresh));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ProductHistoryDTO>> getPriceHistory(@RequestParam String keyword) {
        return ResponseEntity.ok(crawlerService.getProductHistory(keyword));
    }

    @DeleteMapping("/cache")
    public ResponseEntity<String> clearCache(@RequestParam(required = false) String keyword) {
        crawlerService.clearCache(keyword);
        return ResponseEntity.ok("Cache cleared" + (keyword != null ? " for " + keyword : " all"));
    }
}
