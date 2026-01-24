package yan.goodshare.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import yan.goodshare.mapper.UserMapper;
import yan.goodshare.entity.User;
import yan.goodshare.post.PostService;
import yan.goodshare.entity.Post;
import yan.goodshare.user.UserService;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserMapper userMapper;
    private final PostService postService;
    private final UserService userService;
    private final yan.goodshare.recommendation.RecommendationService recommendationService;

    public AdminController(UserMapper userMapper, PostService postService, UserService userService, yan.goodshare.recommendation.RecommendationService recommendationService) {
        this.userMapper = userMapper;
        this.postService = postService;
        this.userService = userService;
        this.recommendationService = recommendationService;
    }

    @GetMapping("/users")
    public ResponseEntity<com.baomidou.mybatisplus.core.metadata.IPage<User>> getAllUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(userMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), null));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/weights")
    public ResponseEntity<java.util.Map<String, Double>> getWeights() {
        return ResponseEntity.ok(recommendationService.getWeights());
    }

    @PostMapping("/weights")
    public ResponseEntity<?> updateWeights(@RequestBody java.util.Map<String, Double> weights) {
        recommendationService.updateWeights(weights);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/posts")
    public ResponseEntity<List<Post>> getAllPosts() {
        return ResponseEntity.ok(postService.getAllPosts());
    }

    @DeleteMapping("/posts")
    public ResponseEntity<?> deletePosts(@RequestBody java.util.Map<String, List<Long>> payload) {
        List<Long> ids = payload.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body("Ids are required");
        }
        postService.deletePosts(ids);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        postService.deletePost(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/posts/pending")
    public ResponseEntity<com.baomidou.mybatisplus.core.metadata.IPage<Post>> getPendingPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(postService.getPendingPosts(page, size));
    }

    @PutMapping("/posts/{id}/status")
    public ResponseEntity<?> updatePostStatus(@PathVariable Long id, @RequestBody java.util.Map<String, Integer> payload) {
        Integer status = payload.get("status");
        if (status == null) {
            return ResponseEntity.badRequest().body("Status is required");
        }
        postService.updatePostStatus(id, status);
        return ResponseEntity.ok().build();
    }
}
