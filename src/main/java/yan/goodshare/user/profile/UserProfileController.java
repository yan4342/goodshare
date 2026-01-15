package yan.goodshare.user.profile;

import org.springframework.beans.factory.annotation.Autowired;
import yan.goodshare.entity.User;
import yan.goodshare.entity.UserProfile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import yan.goodshare.user.UserService;

@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<UserProfile> getCurrentUserProfile(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername()).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        UserProfile userProfile = userService.getUserProfile(user);
        return ResponseEntity.ok(userProfile);
    }

    @PutMapping
    public ResponseEntity<UserProfile> updateCurrentUserProfile(@AuthenticationPrincipal UserDetails userDetails, @RequestBody UserProfileUpdateRequest request) {
        User user = userService.findByUsername(userDetails.getUsername()).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        User updatedUser = userService.updateUserProfile(user, request);
        UserProfile userProfile = userService.getUserProfile(updatedUser);
        return ResponseEntity.ok(userProfile);
    }
}
