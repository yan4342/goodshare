package yan.goodshare.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import yan.goodshare.entity.AppConfig;
import yan.goodshare.mapper.AppConfigMapper;
import yan.goodshare.mapper.UserMapper;
import yan.goodshare.entity.User;
import yan.goodshare.post.PostService;
import yan.goodshare.entity.Post;
import yan.goodshare.user.UserService;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final String FORBIDDEN_WORDS_KEY = "post.forbidden_words";
    private static final String DEFAULT_FORBIDDEN_WORDS = "毒品,枪支,赌博,嫖娼,诈骗";
    private static final String FORBIDDEN_WORDS_DESCRIPTION = "Forbidden words for post title and content";

    private final UserMapper userMapper;
    private final PostService postService;
    private final UserService userService;
    private final yan.goodshare.recommendation.RecommendationService recommendationService;
    private final AppConfigMapper appConfigMapper;

    public AdminController(UserMapper userMapper, PostService postService, UserService userService, yan.goodshare.recommendation.RecommendationService recommendationService, AppConfigMapper appConfigMapper) {
        this.userMapper = userMapper;
        this.postService = postService;
        this.userService = userService;
        this.recommendationService = recommendationService;
        this.appConfigMapper = appConfigMapper;
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

    @GetMapping("/post-moderation/forbidden-words")
    public ResponseEntity<Map<String, Object>> getForbiddenWordsConfig() {
        String rawValue = getForbiddenWordsConfigValue();
        List<String> forbiddenWords = parseForbiddenWords(rawValue);
        return ResponseEntity.ok(Map.of(
                "rawValue", rawValue,
                "forbiddenWords", forbiddenWords,
                "count", forbiddenWords.size()
        ));
    }

    @PutMapping("/post-moderation/forbidden-words")
    public ResponseEntity<?> updateForbiddenWordsConfig(@RequestBody Map<String, String> payload) {
        String normalizedValue = normalizeForbiddenWords(payload.get("rawValue"));
        if (normalizedValue.isEmpty()) {
            return ResponseEntity.badRequest().body("违禁词列表不能为空");
        }

        AppConfig config = appConfigMapper.selectOne(new QueryWrapper<AppConfig>().eq("config_key", FORBIDDEN_WORDS_KEY));
        if (config == null) {
            config = new AppConfig();
            config.setConfigKey(FORBIDDEN_WORDS_KEY);
            config.setDescription(FORBIDDEN_WORDS_DESCRIPTION);
            config.setConfigValue(normalizedValue);
            appConfigMapper.insert(config);
        } else {
            config.setConfigValue(normalizedValue);
            if (config.getDescription() == null || config.getDescription().isBlank()) {
                config.setDescription(FORBIDDEN_WORDS_DESCRIPTION);
            }
            appConfigMapper.updateById(config);
        }

        List<String> forbiddenWords = parseForbiddenWords(normalizedValue);
        return ResponseEntity.ok(Map.of(
                "rawValue", normalizedValue,
                "forbiddenWords", forbiddenWords,
                "count", forbiddenWords.size()
        ));
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

    private String getForbiddenWordsConfigValue() {
        AppConfig config = appConfigMapper.selectOne(new QueryWrapper<AppConfig>().eq("config_key", FORBIDDEN_WORDS_KEY));
        String rawValue = config != null ? config.getConfigValue() : DEFAULT_FORBIDDEN_WORDS;
        return normalizeForbiddenWords(rawValue);
    }

    private List<String> parseForbiddenWords(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawValue.split("[,，;；\\r\\n]+"))
                .map(String::trim)
                .filter(word -> !word.isEmpty())
                .toList();
    }

    private String normalizeForbiddenWords(String rawValue) {
        Set<String> words = new LinkedHashSet<>(parseForbiddenWords(rawValue));
        return words.stream().collect(Collectors.joining(","));
    }
}
