package yan.goodshare.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import yan.goodshare.entity.User;
import yan.goodshare.entity.UserProfile;
import yan.goodshare.follow.FollowService;
import yan.goodshare.user.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final FollowService followService;

    public UserController(UserService userService, FollowService followService) {
        this.userService = userService;
        this.followService = followService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfile> getUserProfile(@PathVariable Long id) {
        User user = userService.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        UserProfile profile = userService.getUserProfile(user);
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/{id}/follow")
    public ResponseEntity<?> followUser(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        // FollowService.followUser checks SecurityContext for current user, but it takes followedId
        // However, I should probably check if user is following themselves here or in service (Service does it)
        try {
            followService.followUser(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/unfollow")
    public ResponseEntity<?> unfollowUser(@PathVariable Long id) {
        try {
            followService.unfollowUser(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}/following")
    public ResponseEntity<List<User>> getFollowing(@PathVariable Long id) {
        return ResponseEntity.ok(followService.getFollowingUsers(id));
    }

    @GetMapping("/{id}/followers")
    public ResponseEntity<List<User>> getFollowers(@PathVariable Long id) {
        return ResponseEntity.ok(followService.getFollowerUsers(id));
    }

    @GetMapping("/{id}/is-following")
    public ResponseEntity<Map<String, Boolean>> checkFollowStatus(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = userService.findByUsername(userDetails.getUsername()).orElse(null);
        boolean isFollowing = false;
        if (currentUser != null) {
            isFollowing = followService.isFollowing(currentUser.getId(), id);
        }
        Map<String, Boolean> result = new HashMap<>();
        result.put("isFollowing", isFollowing);
        return ResponseEntity.ok(result);
    }
}
