package yan.goodshare.search;

import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.elasticsearch.core.SearchHit;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<List<PostDocument>> searchPosts(@RequestParam String query) {
        return ResponseEntity.ok(
            searchService.searchPosts(query).stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList())
        );
    }

    @GetMapping("/hot")
    public ResponseEntity<?> getHotKeywords() {
        return ResponseEntity.ok(searchService.getHotKeywords());
    }
}
