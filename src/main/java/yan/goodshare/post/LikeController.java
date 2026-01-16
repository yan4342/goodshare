package yan.goodshare.post;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts/{postId}/likes")
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @PostMapping
    public ResponseEntity<?> likePost(@PathVariable Long postId) {
        try {
            likeService.likePost(postId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping
    public ResponseEntity<?> unlikePost(@PathVariable Long postId) {
        try {
            likeService.unlikePost(postId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/count")
    public ResponseEntity<Long> getLikeCount(@PathVariable Long postId) {
        return ResponseEntity.ok(likeService.getLikeCount(postId));
    }

    @GetMapping("/status")
    public ResponseEntity<Boolean> getLikeStatus(@PathVariable Long postId) {
        return ResponseEntity.ok(likeService.hasLiked(postId));
    }
}
