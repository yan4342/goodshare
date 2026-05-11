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
import lombok.extern.slf4j.Slf4j;
import java.util.stream.Collectors;

@Slf4j
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
    private final yan.goodshare.service.MessageService messageService;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    public AdminController(UserMapper userMapper, PostService postService, UserService userService, yan.goodshare.recommendation.RecommendationService recommendationService, AppConfigMapper appConfigMapper, yan.goodshare.service.MessageService messageService, org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate) {
        this.userMapper = userMapper;
        this.postService = postService;
        this.userService = userService;
        this.recommendationService = recommendationService;
        this.appConfigMapper = appConfigMapper;
        this.messageService = messageService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/users")
    public ResponseEntity<com.baomidou.mybatisplus.core.metadata.IPage<User>> getAllUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String query) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<User> pageParam = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        if (query != null && !query.trim().isEmpty()) {
            String keyword = query.trim();
            QueryWrapper<User> wrapper = new QueryWrapper<User>()
                    .and(w -> w.like("username", keyword).or().like("nickname", keyword));
            return ResponseEntity.ok(userMapper.selectPage(pageParam, wrapper));
        }
        return ResponseEntity.ok(userMapper.selectPage(pageParam, null));
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
    public ResponseEntity<com.baomidou.mybatisplus.core.metadata.IPage<Post>> getAllPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String query) {
        if (query != null && !query.trim().isEmpty()) {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Post> pageParam = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
            com.baomidou.mybatisplus.core.metadata.IPage<Post> result = postService.searchAdminPosts(query.trim(), pageParam);
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(postService.getAllPosts(null, page, size));
    }

    @DeleteMapping("/posts")
    public ResponseEntity<?> deletePosts(@RequestBody java.util.Map<String, List<Long>> payload) {
        List<Long> ids = payload.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body("Ids are required");
        }
        for (Long id : ids) {
            ResponseEntity<?> errorResponse = deletePostAndNotify(id);
            if (errorResponse != null) {
                return errorResponse;
            }
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        ResponseEntity<?> errorResponse = deletePostAndNotify(id);
        if (errorResponse != null) {
            return errorResponse;
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/posts/pending")
    public ResponseEntity<com.baomidou.mybatisplus.core.metadata.IPage<Post>> getPendingPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(postService.getPendingPosts(page, size));
    }

    @PutMapping("/posts/{id}/status")
    public ResponseEntity<?> updatePostStatus(@PathVariable Long id, @RequestBody java.util.Map<String, Object> payload) {
        Object statusObj = payload.get("status");
        if (statusObj == null) {
            return ResponseEntity.badRequest().body("Status is required");
        }
        Integer status = statusObj instanceof Integer ? (Integer) statusObj : Integer.valueOf(statusObj.toString());
        
        Post post = postService.getPostById(id);
        if (post == null) {
            return ResponseEntity.badRequest().body("Post not found");
        }
        
        postService.updatePostStatus(id, status);
        
        // If rejected (status = 2), send system message
        if (status == 2) {
            String reasonCategory = (String) payload.get("reasonCategory");
            String reasonDetail = (String) payload.get("reasonDetail");
            
            if (reasonCategory == null || reasonCategory.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Reason category is required for rejection");
            }
            
            User systemUser = userService.getSystemUser();
            String messageContent = String.format("您的帖子《%s》审核未通过。\n原因：%s", 
                    post.getTitle() != null ? post.getTitle() : "无标题", reasonCategory);
                    
            if (reasonDetail != null && !reasonDetail.trim().isEmpty()) {
                messageContent += "\n详细说明：" + reasonDetail;
            }
            
            // Append post link marker for frontend rendering
            messageContent += "\n[POST:" + post.getId() + "]";
            
            yan.goodshare.entity.Message savedMsg = messageService.sendMessage(systemUser.getId(), post.getUserId(), messageContent);
            savedMsg.setSender(systemUser); // Populate sender info for frontend
            
            // Broadcast via WebSocket
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(post.getUserId()),
                    "/queue/messages",
                    savedMsg
            );
        }
        
        return ResponseEntity.ok().build();
    }

    /**
     * Delete a post and send a system message to the author.
     * Returns null on success, or a ResponseEntity with an error on failure.
     */
    private ResponseEntity<?> deletePostAndNotify(Long id) {
        Post post = postService.getPostById(id);
        String postTitle = post != null ? (post.getTitle() != null ? post.getTitle() : "无标题") : null;
        Long authorId = post != null ? post.getUserId() : null;

        try {
            postService.deletePost(id);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("删除帖子失败: " + e.getMessage());
        }

        // Notify the author after successful deletion
        if (authorId != null) {
            String messageContent = String.format("您的帖子《%s》因违规已被管理员删除。", postTitle);
            try {
                User systemUser = userService.getSystemUser();
                yan.goodshare.entity.Message savedMsg = messageService.sendMessage(systemUser.getId(), authorId, messageContent);
                savedMsg.setSender(systemUser);
                messagingTemplate.convertAndSendToUser(
                        String.valueOf(authorId),
                        "/queue/messages",
                        savedMsg
                );
            } catch (Exception e) {
                // Log but don't fail the delete operation for notification errors
                log.error("帖子删除通知发送失败 (帖子ID: {}): {}", id, e.getMessage());
            }
        }
        return null; // success
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
