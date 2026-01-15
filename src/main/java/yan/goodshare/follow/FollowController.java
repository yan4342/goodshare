package yan.goodshare.follow;

import yan.goodshare.entity.Follow;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/follows")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping("/{followedId}")
    public ResponseEntity<?> followUser(@PathVariable Long followedId) {
        try {
            followService.followUser(followedId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{followedId}")
    public ResponseEntity<?> unfollowUser(@PathVariable Long followedId) {
        try {
            followService.unfollowUser(followedId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/following")
    public ResponseEntity<List<Follow>> getFollowing() {
        return ResponseEntity.ok(followService.getFollowing());
    }

    @GetMapping("/followers")
    public ResponseEntity<List<Follow>> getFollowers() {
        return ResponseEntity.ok(followService.getFollowers());
    }

    @GetMapping("/{userId}/following/count")
    public ResponseEntity<Long> getFollowingCount(@PathVariable Long userId) {
        return ResponseEntity.ok(followService.getFollowingCount(userId));
    }

    @GetMapping("/{userId}/followers/count")
    public ResponseEntity<Long> getFollowersCount(@PathVariable Long userId) {
        return ResponseEntity.ok(followService.getFollowersCount(userId));
    }
}
