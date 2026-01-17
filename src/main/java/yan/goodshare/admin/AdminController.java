package yan.goodshare.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import yan.goodshare.mapper.UserMapper;
import yan.goodshare.entity.User;
import yan.goodshare.post.PostService;
import yan.goodshare.entity.Post;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserMapper userMapper;
    private final PostService postService;

    public AdminController(UserMapper userMapper, PostService postService) {
        this.userMapper = userMapper;
        this.postService = postService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userMapper.selectList(null));
    }

    @GetMapping("/posts")
    public ResponseEntity<List<Post>> getAllPosts() {
        return ResponseEntity.ok(postService.getAllPosts());
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        postService.deletePost(id);
        return ResponseEntity.ok().build();
    }
}
