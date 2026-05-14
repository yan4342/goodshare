package yan.goodshare.recommendation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import yan.goodshare.entity.Post;

import java.util.List;

// 推荐控制器，提供推荐功能
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    @Autowired
    private RecommendationService recommendationService;

    // 获取用户推荐帖子（正常模式，过滤已浏览帖子）
    @GetMapping
    public ResponseEntity<List<Post>> getRecommendations(
            @RequestParam("user_id") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Post> recommendations = recommendationService.getRecommendations(userId, page, size);
        return ResponseEntity.ok(recommendations);
    }

    // 评估专用接口：不过滤已浏览帖子，用于 Leave-One-Out 评估
    @GetMapping("/eval")
    public ResponseEntity<List<Post>> getRecommendationsForEval(
            @RequestParam("user_id") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Post> recommendations = recommendationService.getRecommendationsForEval(userId, page, size);
        return ResponseEntity.ok(recommendations);
    }
}
