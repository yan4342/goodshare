package yan.goodshare.post;

import jakarta.validation.Valid;
import yan.goodshare.entity.Comment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public ResponseEntity<?> createComment(@PathVariable Long postId, @Valid @RequestBody CommentRequest commentRequest) {
        try {
            Comment createdComment = commentService.createComment(postId, commentRequest);
            return ResponseEntity.ok(createdComment);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<Comment>> getCommentsByPostId(@PathVariable Long postId) {
        return ResponseEntity.ok(commentService.getCommentsByPostId(postId));
    }

    @PostMapping("/{commentId}/like")
    public ResponseEntity<?> likeComment(@PathVariable Long commentId) {
        commentService.likeComment(commentId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{commentId}/like")
    public ResponseEntity<?> unlikeComment(@PathVariable Long commentId) {
        commentService.unlikeComment(commentId);
        return ResponseEntity.ok().build();
    }
}
