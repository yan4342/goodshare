package yan.goodshare.post;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import yan.goodshare.entity.Post;
import yan.goodshare.entity.User;
import yan.goodshare.user.UserService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final UserService userService;

    public PostController(PostService postService, UserService userService) {
        this.postService = postService;
        this.userService = userService;
    }

    // 创建帖子
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createPost(@Valid @RequestBody PostRequest postRequest) {
        try {
            Post createdPost = postService.createPost(postRequest);
            return ResponseEntity.ok(createdPost);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 更新帖子
    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updatePost(@PathVariable Long id, @Valid @RequestBody PostRequest postRequest) {
        try {
            Post updatedPost = postService.updatePost(id, postRequest);
            return ResponseEntity.ok(updatedPost);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 获取所有帖子
    @GetMapping
    public ResponseEntity<IPage<Post>> getPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String tag) {
        return ResponseEntity.ok(postService.getAllPosts(tag, page, size));
    }

    @GetMapping("/followed")
    public ResponseEntity<IPage<Post>> getFollowedPosts(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(postService.getFollowedPosts(user.getId(), page, size));
    }

    // 获取帖子详情
    @GetMapping("/{id}")
    public ResponseEntity<?> getPostById(@PathVariable Long id) {
        try {
            Post post = postService.getPostById(id);
            return ResponseEntity.ok(post);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 获取用户发布的所有帖子
    @GetMapping("/user/{userId}")
    
    public ResponseEntity<IPage<Post>> getPostsByUserId(
        @PathVariable Long userId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(postService.getPostsByUserId(userId, page, size));
    }

    // 删除帖子
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        try {
            postService.deletePost(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 记录帖子查看
    @PostMapping("/{id}/view")
    public ResponseEntity<?> recordView(@PathVariable Long id) {
        // Can be anonymous or authenticated
        String username = null;
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                if (auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails) {
                    username = ((org.springframework.security.core.userdetails.UserDetails) auth.getPrincipal()).getUsername();
                } else {
                    username = auth.getName();
                }
            }
        } catch (Exception e) {
            // Ignore auth errors, treat as anonymous
        }
        
        postService.recordView(id, username);
        return ResponseEntity.ok().build();
    }


    @PostMapping("/{id}/dislike")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> dislikePost(@PathVariable Long id) {
        try {
            postService.dislikePost(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 重新索引所有帖子
    @PostMapping("/reindex")
    // @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> reindexAllPosts() {
        try {
            postService.reindexAllPosts();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Reindex failed: " + e.getMessage());
        }
    }
    @GetMapping("/history")
    public ResponseEntity<?> getHistoryPosts(@AuthenticationPrincipal UserDetails userDetails,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(postService.getHistoryPosts(user.getId(), page, size));
    }
    
}
