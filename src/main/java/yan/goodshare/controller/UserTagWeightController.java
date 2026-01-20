package yan.goodshare.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import yan.goodshare.entity.User;
import yan.goodshare.entity.UserTagWeight;
import yan.goodshare.service.UserTagWeightService;
import yan.goodshare.user.UserService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/weights")
public class UserTagWeightController {

    @Autowired
    private UserTagWeightService userTagWeightService;

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<List<UserTagWeight>> getMyWeights() {
        User user = getCurrentUser();
        return ResponseEntity.ok(userTagWeightService.getUserWeights(user.getId()));
    }

    @PostMapping
    public ResponseEntity<?> updateWeight(@RequestBody Map<String, Object> payload) {
        User user = getCurrentUser();
        Long tagId = Long.valueOf(payload.get("tagId").toString());
        Double weight = Double.valueOf(payload.get("weight").toString());
        
        userTagWeightService.updateUserWeight(user.getId(), tagId, weight);
        return ResponseEntity.ok().build();
    }

    private User getCurrentUser() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userService.findByUsername(userDetails.getUsername()).orElseThrow(() -> new RuntimeException("User not found"));
    }
}
