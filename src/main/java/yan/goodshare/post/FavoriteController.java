package yan.goodshare.post;

import yan.goodshare.entity.Favorite;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/{postId}")
    public ResponseEntity<?> favoritePost(@PathVariable Long postId) {
        try {
            favoriteService.favoritePost(postId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<?> unfavoritePost(@PathVariable Long postId) {
        try {
            favoriteService.unfavoritePost(postId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<Favorite>> getUserFavorites() {
        return ResponseEntity.ok(favoriteService.getUserFavorites());
    }
}
