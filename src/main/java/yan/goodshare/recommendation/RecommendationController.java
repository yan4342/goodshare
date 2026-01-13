package yan.goodshare.recommendation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    @Autowired
    private RecommendationService recommendationService;

    @GetMapping
    public ResponseEntity<String> getRecommendations(@RequestParam("user_id") Long userId) {
        try {
            String recommendations = recommendationService.getRecommendations(userId);
            return ResponseEntity.ok(recommendations);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error fetching recommendations");
        }
    }
}
