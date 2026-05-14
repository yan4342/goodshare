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
    public ResponseEntity<List<PostDocument>> searchPosts(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String tag
    ) {
        if ((query == null || query.trim().isEmpty()) && (tag == null || tag.trim().isEmpty())) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(
            searchService.searchPosts(query, tag).stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList())
        );
    }

    @GetMapping("/users")
    public ResponseEntity<List<yan.goodshare.entity.User>> searchUsers(@RequestParam String query) {
        return ResponseEntity.ok(searchService.searchUsers(query));
    }

    @GetMapping("/suggest")
    public ResponseEntity<List<String>> suggest(@RequestParam String query) {
        return ResponseEntity.ok(searchService.suggest(query));
    }

    @GetMapping("/hot")
    public ResponseEntity<?> getHotKeywords() {
        return ResponseEntity.ok(searchService.getHotKeywords());
    }

    @GetMapping("/tokenize")
    public ResponseEntity<java.util.List<String>> tokenize(@RequestParam String text) {
        return ResponseEntity.ok(searchService.tokenize(text));
    }
}
