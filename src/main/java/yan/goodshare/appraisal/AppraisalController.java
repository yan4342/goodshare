package yan.goodshare.appraisal;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import yan.goodshare.entity.Appraisal;
import yan.goodshare.entity.User;
import yan.goodshare.mapper.UserMapper;

import java.util.Map;

@RestController
@RequestMapping("/api/appraisals")
public class AppraisalController {

    private final AppraisalService appraisalService;
    private final UserMapper userMapper;

    public AppraisalController(AppraisalService appraisalService, UserMapper userMapper) {
        this.appraisalService = appraisalService;
        this.userMapper = userMapper;
    }

    @GetMapping
    public Page<Appraisal> list(@RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "10") int size,
                                @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = null;
        if (userDetails != null) {
            userId = getUserIdFromUserDetails(userDetails);
        }
        return appraisalService.getAppraisals(page, size, userId);
    }

    @GetMapping("/user/{userId}")
    public Page<Appraisal> listByUser(@PathVariable Long userId,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return appraisalService.getUserAppraisals(page, size, userId);
    }

    @GetMapping("/{id}")
    public Appraisal detail(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = null;
        if (userDetails != null) {
            userId = getUserIdFromUserDetails(userDetails);
        }
        return appraisalService.getAppraisalDetail(id, userId);
    }

    @PostMapping
    public Appraisal create(@RequestBody Map<String, Object> payload, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized");
        }
        Long userId = getUserIdFromUserDetails(userDetails);
        
        String productName = (String) payload.get("productName");
        String description = (String) payload.get("description");
        Object imagesObj = payload.get("images");
        String images = "[]";
        if (imagesObj != null) {
            // Assuming frontend sends JSON array string or List, but here let's assume JSON string for simplicity or convert list to string
            // Actually it's better to handle List in DTO, but map is flexible.
            // If it's a List from JSON, Jackson maps it to ArrayList.
            if (imagesObj instanceof String) {
                images = (String) imagesObj;
            } else {
                // Convert List to String manually or use ObjectMapper (omitted for brevity, assume frontend sends JSON string or handled by Jackson default)
                // Let's assume frontend sends a JSON string of URLs.
                images = imagesObj.toString();
            }
        }
        
        return appraisalService.createAppraisal(userId, productName, description, images);
    }

    @PostMapping("/{id}/vote")
    public void vote(@PathVariable Long id, @RequestBody Map<String, Integer> payload, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized");
        }
        Long userId = getUserIdFromUserDetails(userDetails);
        Integer voteType = payload.get("voteType"); // 1: Real, 2: Fake
        if (voteType == null || (voteType != 1 && voteType != 2)) {
            throw new IllegalArgumentException("Invalid vote type");
        }
        
        appraisalService.vote(userId, id, voteType);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized");
        }
        Long userId = getUserIdFromUserDetails(userDetails);
        appraisalService.deleteAppraisal(id, userId);
    }

    private Long getUserIdFromUserDetails(UserDetails userDetails) {
        // Assuming username is unique and we can fetch ID
        // Or if UserDetails is our custom implementation that has ID
        // Let's fetch from DB to be safe
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User> queryWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        queryWrapper.eq("username", userDetails.getUsername());
        User user = userMapper.selectOne(queryWrapper);
        return user != null ? user.getId() : null;
    }
}
