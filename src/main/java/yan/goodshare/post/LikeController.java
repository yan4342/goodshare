package yan.goodshare.post;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import yan.goodshare.entity.Post;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @PostMapping("/posts/{postId}/likes")
    public ResponseEntity<?> likePost(@PathVariable Long postId) {
        try {
            likeService.likePost(postId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/posts/{postId}/likes")
    public ResponseEntity<?> unlikePost(@PathVariable Long postId) {
        try {
            likeService.unlikePost(postId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/posts/{postId}/likes/count")
    public ResponseEntity<Long> getLikeCount(@PathVariable Long postId) {
        return ResponseEntity.ok(likeService.getLikeCount(postId));
    }

    @GetMapping("/posts/{postId}/likes/status")
    public ResponseEntity<Boolean> getLikeStatus(@PathVariable Long postId) {
        return ResponseEntity.ok(likeService.hasLiked(postId));
    }

    @GetMapping("/likes")
    public ResponseEntity<List<Post>> getUserLikedPosts() {
        return ResponseEntity.ok(likeService.getUserLikedPosts());
    }
}
