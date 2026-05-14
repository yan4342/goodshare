package yan.goodshare.recommendation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import yan.goodshare.entity.Comment;
import yan.goodshare.entity.Favorite;
import yan.goodshare.entity.Like;
import yan.goodshare.entity.Post;
import yan.goodshare.entity.PostView;
import yan.goodshare.mapper.CommentMapper;
import yan.goodshare.mapper.FavoriteMapper;
import yan.goodshare.mapper.LikeMapper;
import yan.goodshare.mapper.PostMapper;
import yan.goodshare.mapper.PostViewMapper;
import yan.goodshare.mapper.AppConfigMapper;
import yan.goodshare.mapper.UserTagWeightMapper;
import yan.goodshare.entity.AppConfig;
import yan.goodshare.entity.UserTagWeight;
import yan.goodshare.search.PostDocument;
import yan.goodshare.search.SearchService;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@Slf4j
@Service
public class RecommendationService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private LikeMapper likeMapper;

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostViewMapper postViewMapper;

    @Autowired
    private AppConfigMapper appConfigMapper;

    @Autowired
    private UserTagWeightMapper userTagWeightMapper;

    @Autowired
    private SearchService searchService;

    private Map<String, Double> weights = new HashMap<>();

//    private static final int NEIGHBOR_COUNT = 10;已弃用
    private static final int RECOMMENDATION_COUNT = 10;
    private static final int DATA_LIMIT = 1000; // Limit interactions to recent 1000 for performance
    private static final double STRONG_REJECT_THRESHOLD = 0.6;
    private static final int GLOBAL_ADJUST_LOG_TOP_N = 10;

    private final Random random = new Random();

    @PostConstruct
    public void init() {
        refreshWeights();
    }

    public void refreshWeights() {
        try {
            List<AppConfig> configs = appConfigMapper.selectList(new QueryWrapper<AppConfig>().likeRight("config_key", "weight."));
            for (AppConfig config : configs) {
                try {
                    weights.put(config.getConfigKey(), Double.parseDouble(config.getConfigValue()));
                } catch (NumberFormatException e) {
                    log.error("权重值无效: key={}, value={}", config.getConfigKey(), config.getConfigValue());
                }
            }
            // Ensure defaults
            weights.putIfAbsent("weight.view", 0.5);
            weights.putIfAbsent("weight.like", 1.0);
            weights.putIfAbsent("weight.favorite", 3.0);
            weights.putIfAbsent("weight.comment", 2.0);
            weights.putIfAbsent("weight.content", 1.0);
            weights.putIfAbsent("weight.comment_count", 0.1);
            weights.putIfAbsent("weight.view_count", 0.05);
        } catch (Exception e) {
            log.error("从数据库加载权重失败: {}", e.getMessage());
            // Fallback defaults
            weights.put("weight.view", 0.5);
            weights.put("weight.like", 1.0);
            weights.put("weight.favorite", 3.0);
            weights.put("weight.comment", 2.0);
            weights.put("weight.content", 1.0);
            weights.put("weight.comment_count", 0.1);
            weights.put("weight.view_count", 0.05);
        }
    }

    public Map<String, Double> getWeights() {
        return new HashMap<>(weights);
    }

    public void updateWeights(Map<String, Double> newWeights) {
        for (Map.Entry<String, Double> entry : newWeights.entrySet()) {
            String key = entry.getKey();
            Double value = entry.getValue();
            if (value != null) {
                AppConfig config = appConfigMapper.selectOne(new QueryWrapper<AppConfig>().eq("config_key", key));
                if (config != null) {
                    config.setConfigValue(String.valueOf(value));
                    appConfigMapper.updateById(config);
                } else {
                    config = new AppConfig();
                    config.setConfigKey(key);
                    config.setConfigValue(String.valueOf(value));
                    config.setDescription("Weight for " + key.replace("weight.", "") + " interaction");
                    appConfigMapper.insert(config);
                }
            }
        }
        refreshWeights();
    }

    public List<Post> getRecommendations(Long userId, int page, int size) {
        return getRecommendationsInternal(userId, page, size, true);
    }

    // 评估专用：不过滤已浏览帖子
    public List<Post> getRecommendationsForEval(Long userId, int page, int size) {
        return getRecommendationsInternal(userId, page, size, false);
    }

    private List<Post> getRecommendationsInternal(Long userId, int page, int size, boolean excludeViewed) {
        long startTime = System.currentTimeMillis();
        log.info("开始为用户 {} 生成推荐 (页码: {}, 数量: {}, excludeViewed={})", userId, page, size, excludeViewed);

        // Optimize: Fetch all viewed post IDs by this user first
        Set<Long> excludedPostIds = new HashSet<>();
        
        // 1. Fetch Viewed Posts (only when excludeViewed=true)
        if (excludeViewed) {
            List<Object> viewIds = postViewMapper.selectObjs(new QueryWrapper<PostView>()
                    .select("post_id")
                    .eq("user_id", userId));
            if (viewIds != null) {
                for (Object id : viewIds) {
                    if (id != null) {
                        excludedPostIds.add(((Number) id).longValue());
                    }
                }
            }
        }

        // 2. Fetch Authored Posts (Don't recommend own posts)
        List<Object> authoredIds = postMapper.selectObjs(new QueryWrapper<Post>()
                .select("id")
                .eq("user_id", userId));
        if (authoredIds != null) {
            for (Object id : authoredIds) {
                if (id != null) {
                    excludedPostIds.add(((Number) id).longValue());
                }
            }
        }

        // 0. New User / Cold Start Check
        // If user has no interactions, directly recommend hot posts to avoid unnecessary computation
        if (isNewUser(userId)) {
            log.info("检测到新用户 {} (无交互数据)。返回热门帖子作为冷启动推荐。", userId);
            int offset = (page - 1) * size;
            // FIX: Pass excludedPostIds to exclude watched/owned content
            return postMapper.selectHotPostsWithUser(size, offset, new ArrayList<>(excludedPostIds));
        }

        // 1. Fetch target user's interactions only (for Step 5 seed selection)
        // NOTE: Full user-item matrix is no longer needed — CF is handled by Python microservice.
        log.debug("正在获取用户 {} 的交互数据 (用于内容推荐种子选择)...", userId);
        Map<Long, Double> targetUserInteractions = new HashMap<>();

        List<PostView> targetViews = postViewMapper.selectList(new QueryWrapper<PostView>().eq("user_id", userId).orderByDesc("id").last("LIMIT " + DATA_LIMIT));
        for (PostView v : targetViews) {
            targetUserInteractions.merge(v.getPostId(), weights.getOrDefault("weight.view", 0.5), (a, b) -> a + b);
        }
        List<Like> targetLikes = likeMapper.selectList(new QueryWrapper<Like>().eq("user_id", userId).orderByDesc("id").last("LIMIT " + DATA_LIMIT));
        for (Like l : targetLikes) {
            targetUserInteractions.merge(l.getPostId(), weights.getOrDefault("weight.like", 1.0), (a, b) -> a + b);
        }
        List<Favorite> targetFavorites = favoriteMapper.selectList(new QueryWrapper<Favorite>().eq("user_id", userId).orderByDesc("id").last("LIMIT " + DATA_LIMIT));
        for (Favorite f : targetFavorites) {
            targetUserInteractions.merge(f.getPostId(), weights.getOrDefault("weight.favorite", 3.0), (a, b) -> a + b);
        }
        List<Comment> targetComments = commentMapper.selectList(new QueryWrapper<Comment>().eq("user_id", userId).orderByDesc("id").last("LIMIT " + DATA_LIMIT));
        for (Comment c : targetComments) {
            targetUserInteractions.merge(c.getPostId(), weights.getOrDefault("weight.comment", 2.0), (a, b) -> a + b);
        }

        log.debug("用户 {} 交互数据获取完成，涉及 {} 篇帖子，耗时: {}ms", userId, targetUserInteractions.size(), (System.currentTimeMillis() - startTime));

        Map<Long, Double> recommendedPosts = new HashMap<>();

        // 3. Collaborative Filtering (Python Microservice)
        try {
            log.info("调用 Python 协同过滤微服务...");
            // Call Python Service
            // 注意：在 Docker 环境中，goodshare-server 访问宿主机的 5000 端口需要用 host.docker.internal 或者服务名
            // 为了兼容本地和 Docker 环境，优先尝试 recommendation-service，然后回退
            String url = "http://recommendation-service:5000/recommend?user_id=" + userId + "&limit=40";
            
            try {
                ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, 
                    HttpMethod.GET, 
                    null, 
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
                );
                
                List<Map<String, Object>> cfRecs = response.getBody();
                if (cfRecs != null) {
                    log.info("Python 协同过滤微服务返回了 {} 条推荐结果。", cfRecs.size());
                    for (Map<String, Object> rec : cfRecs) {
                        Long postId = ((Number) rec.get("post_id")).longValue();
                        Double score = ((Number) rec.get("score")).doubleValue();
                        
                        // Filter out items already viewed/authored (unified with excludedPostIds)
                        if (!excludedPostIds.contains(postId)) {
                            recommendedPosts.merge(postId, score, (a, b) -> a + b);
                        }
                    }
                }
            } catch (Exception innerE) {
                log.warn("通过 recommendation-service 调用失败，尝试回退到 localhost:5000 : {}", innerE.getMessage());
                url = "http://localhost:5000/recommend?user_id=" + userId + "&limit=40";
                ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, 
                    HttpMethod.GET, 
                    null, 
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
                );
                
                List<Map<String, Object>> cfRecs = response.getBody();
                if (cfRecs != null) {
                    log.info("Python 协同过滤微服务 (localhost) 返回了 {} 条推荐结果。", cfRecs.size());
                    for (Map<String, Object> rec : cfRecs) {
                        Long postId = ((Number) rec.get("post_id")).longValue();
                        Double score = ((Number) rec.get("score")).doubleValue();
                        
                        // Filter out items already viewed/authored (unified with excludedPostIds)
                        if (!excludedPostIds.contains(postId)) {
                            recommendedPosts.merge(postId, score, (a, b) -> a + b);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("调用 Python 推荐微服务最终失败: {}", e.getMessage());
            // Fallback: If Python service fails, we might want to use the local logic or just skip CF
            // For now, we log and skip, relying on Hot Posts fallback later.
        }

        // 4. Partition Recommendation (Tag-based Boosting — prioritize CF candidates)
        log.info("开始执行基于标签权重的分区推荐调整...");
        List<UserTagWeight> userWeights = userTagWeightMapper.selectList(new QueryWrapper<UserTagWeight>().eq("user_id", userId));

        // Pre-fetch tags for all CF candidates to enable tag-based boosting on CF results
        Map<Long, List<Long>> cfCandidateTagMap = new HashMap<>();
        if (!recommendedPosts.isEmpty()) {
            List<Long> cfCandidateIds = new ArrayList<>(recommendedPosts.keySet());
            int tagBatchSize = 100;
            for (int i = 0; i < cfCandidateIds.size(); i += tagBatchSize) {
                int end = Math.min(cfCandidateIds.size(), i + tagBatchSize);
                List<Long> batchIds = cfCandidateIds.subList(i, end);
                List<Map<String, Object>> postTags = postMapper.selectTagsByPostIds(batchIds);
                for (Map<String, Object> row : postTags) {
                    Long pId = ((Number) row.get("post_id")).longValue();
                    Long tId = ((Number) row.get("id")).longValue();
                    cfCandidateTagMap.computeIfAbsent(pId, k -> new ArrayList<>()).add(tId);
                }
            }
            log.info("为 {} 篇协同过滤候选预取标签完成，命中标签的帖子 {} 篇。", cfCandidateIds.size(), cfCandidateTagMap.size());
        }

        // Build reverse map: tagId -> list of CF candidate postIds
        Map<Long, List<Long>> tagToCfCandidates = new HashMap<>();
        for (Map.Entry<Long, List<Long>> entry : cfCandidateTagMap.entrySet()) {
            Long postId = entry.getKey();
            for (Long tagId : entry.getValue()) {
                tagToCfCandidates.computeIfAbsent(tagId, k -> new ArrayList<>()).add(postId);
            }
        }

        final int TAG_LIMIT = 5;

        if (userWeights != null && !userWeights.isEmpty()) {
            for (UserTagWeight uw : userWeights) {
                if (Math.abs(uw.getWeight() - 1.0) > 0.01) {
                    double boost = (uw.getWeight() - 1.0) * 2.0;// - 1.0 是为了区分加分和减分，乘以 2.0 是为了放大效果（可以调整这个放大倍数）
                    int cfUsed = 0;

                    // Phase A: Boost/penalize CF candidates that match this tag
                    List<Long> cfMatched = tagToCfCandidates.getOrDefault(uw.getTagId(), Collections.emptyList());
                    for (Long postId : cfMatched) {
                        if (cfUsed >= TAG_LIMIT) break;
                        if (!excludedPostIds.contains(postId)) {
                            double randomNoise = random.nextDouble() * 0.01;
                            if (boost > 0) {
                                recommendedPosts.merge(postId, boost + randomNoise, (a, b) -> a + b);
                            } else {
                                recommendedPosts.merge(postId, boost, (a, b) -> a + b);
                                if (recommendedPosts.get(postId) < 0) {
                                    recommendedPosts.remove(postId);
                                }
                            }
                            cfUsed++;
                        }
                    }

                    // Phase B: If not enough, fetch remaining from DB (positive weights only)
                    if (cfUsed < TAG_LIMIT && boost > 0) {
                        int needed = TAG_LIMIT - cfUsed;
                        List<Post> tagPosts = postMapper.selectPostsByTagId(uw.getTagId(), 20);
                        // Exclude already-candidate posts and excluded posts
                        Set<Long> alreadyCandidate = new HashSet<>(recommendedPosts.keySet());
                        tagPosts = tagPosts.stream()
                                .filter(p -> !alreadyCandidate.contains(p.getId()) && !excludedPostIds.contains(p.getId()))
                                .collect(Collectors.toList());
                        Collections.shuffle(tagPosts);
                        tagPosts = tagPosts.stream().limit(needed).collect(Collectors.toList());

                        for (Post p : tagPosts) {
                            double randomNoise = random.nextDouble() * 0.01;
                            recommendedPosts.merge(p.getId(), boost + randomNoise, (a, b) -> a + b);
                        }
                        if (!tagPosts.isEmpty()) {
                            log.debug("标签 {} (权重={}) 从DB补充 {} 篇帖子。", uw.getTagId(), uw.getWeight(), tagPosts.size());
                        }
                    }
                }
            }
        }

        // 5. Content-Based Recommendation (Elasticsearch MoreLikeThis)
        log.info("开始执行基于内容的推荐召回 (Elasticsearch)...");
        // Select top 3 interacted posts as seeds
        List<Long> topInteractedPosts = targetUserInteractions.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (Long seedId : topInteractedPosts) {
            try {
                SearchHits<PostDocument> similarDocs = searchService.findSimilarPosts(seedId.toString());
                for (SearchHit<PostDocument> hit : similarDocs) {
                    try {
                        Long similarId = Long.valueOf(hit.getContent().getId());
                        if (!excludedPostIds.contains(similarId) && !similarId.equals(seedId)) {
                            // Boost score: Fixed boost 1.0 per occurrence or based on ES score
                            // Using a moderate boost to complement UserCF
                            double boost = weights.getOrDefault("weight.content", 1.0);
                            double randomNoise = random.nextDouble() * 0.01;
                            recommendedPosts.merge(similarId, boost + randomNoise, (a, b) -> a + b);
                        }
                    } catch (NumberFormatException e) {
                        // Ignore invalid IDs
                    }
                }
            } catch (Exception e) {
                log.error("基于内容的推荐召回失败，种子帖子ID {}: {}", seedId, e.getMessage());
            }
        }

        // 5.5 Popularity Boosting (View Count + Comment Count)
        log.info("开始应用浏览量和评论热度加分策略...");
        if (!recommendedPosts.isEmpty()) {
            List<Long> candidateIds = new ArrayList<>(recommendedPosts.keySet());
            int batchSize = 100;
            double commentWeight = weights.getOrDefault("weight.comment_count", 0.1);
            double viewWeight = weights.getOrDefault("weight.view_count", 0.05);

            for (int i = 0; i < candidateIds.size(); i += batchSize) {
                int end = Math.min(candidateIds.size(), i + batchSize);
                List<Long> batchIds = candidateIds.subList(i, end);

                // 浏览量加分
                if (viewWeight > 0.001) {
                    List<Map<String, Object>> viewCounts = postMapper.selectViewCountsByPostIds(batchIds);
                    for (Map<String, Object> row : viewCounts) {
                        Long pId = ((Number) row.get("post_id")).longValue();
                        Long count = ((Number) row.get("view_count")).longValue();
                        if (count > 0) {
                            double boost = Math.log1p(count) * viewWeight;//对数加分，防止爆炸
                            recommendedPosts.merge(pId, boost, (a, b) -> a + b);
                        }
                    }
                }

                // 评论数加分
                if (commentWeight > 0.001) {
                    List<Map<String, Object>> counts = postMapper.selectCommentCountsByPostIds(batchIds);
                    for (Map<String, Object> row : counts) {
                        Long pId = ((Number) row.get("post_id")).longValue();
                        Long count = ((Number) row.get("comment_count")).longValue();
                        if (count > 0) {
                            double boost = Math.log1p(count) * commentWeight;
                            recommendedPosts.merge(pId, boost, (a, b) -> a + b);
                        }
                    }
                }
            }
        }

        // 6. Global Weight Adjustment (Apply Tag Penalties/Boosts to ALL candidates)
        log.info("开始应用全局标签权重调整 (惩罚/加分)...");
        if (!recommendedPosts.isEmpty()) {
            log.info("全局调整前 Top{}: {}", GLOBAL_ADJUST_LOG_TOP_N, formatTopScores(recommendedPosts, GLOBAL_ADJUST_LOG_TOP_N));
        }
        if (userWeights != null && !userWeights.isEmpty() && !recommendedPosts.isEmpty()) {
            // Filter weights that are significantly different from 1.0
            Map<Long, Double> activeTagWeights = new HashMap<>();
            for (UserTagWeight uw : userWeights) {
                if (Math.abs(uw.getWeight() - 1.0) > 0.01) {
                    activeTagWeights.put(uw.getTagId(), uw.getWeight());
                }
            }

            if (!activeTagWeights.isEmpty()) {
                int removedByStrongReject = 0;
                int adjustedByMultiplier = 0;
                long lowWeightCount = activeTagWeights.values().stream().filter(w -> w <= STRONG_REJECT_THRESHOLD).count();
                long neutralToLowCount = activeTagWeights.values().stream().filter(w -> w > STRONG_REJECT_THRESHOLD && w < 1.0).count();
                long highWeightCount = activeTagWeights.values().stream().filter(w -> w > 1.0).count();
                log.info("活跃标签权重分布: <=强惩罚阈值({})={}个, (阈值,1.0)={}个, >1.0={}个, 合计={}个。",
                        STRONG_REJECT_THRESHOLD, lowWeightCount, neutralToLowCount, highWeightCount, activeTagWeights.size());

                List<Long> candidateIds = new ArrayList<>(recommendedPosts.keySet());
                int strongRejectMatchedCandidates = 0;
                // Batch fetch tags for all candidate posts
                // Split into batches if too many (e.g., 100) to avoid SQL limits
                int batchSize = 100;
                for (int i = 0; i < candidateIds.size(); i += batchSize) {
                    int end = Math.min(candidateIds.size(), i + batchSize);
                    List<Long> batchIds = candidateIds.subList(i, end);
                    
                    List<Map<String, Object>> postTags = postMapper.selectTagsByPostIds(batchIds);
                    
                    // Group tags by post ID
                    Map<Long, List<Long>> postTagMap = new HashMap<>();
                    for (Map<String, Object> row : postTags) {
                        Long pId = ((Number) row.get("post_id")).longValue();
                        Long tId = ((Number) row.get("id")).longValue();
                        postTagMap.computeIfAbsent(pId, k -> new ArrayList<>()).add(tId);
                    }

                    // Apply adjustments
                    for (Long pId : batchIds) {
                        if (postTagMap.containsKey(pId)) {
                            List<Long> tags = postTagMap.get(pId);
                            double finalMultiplier = 1.0;
                            boolean shouldRemove = false;

                            for (Long tId : tags) {
                                if (activeTagWeights.containsKey(tId)) {
                                    double w = activeTagWeights.get(tId);
                                    if (w <= STRONG_REJECT_THRESHOLD) {//STRONG_REJECT_THRESHOLD=
                                        // Strong reject
                                        shouldRemove = true;
                                        break;
                                    }
                                    // Cumulative multiplier: 0.5 * 0.5 = 0.25 (Very weak)
                                    // 2.0 * 2.0 = 4.0 (Very strong)
                                    finalMultiplier *= w; 
                                }
                            }
                            
                            if (shouldRemove) {
                                strongRejectMatchedCandidates++;
                                if (recommendedPosts.remove(pId) != null) {
                                    removedByStrongReject++;
                                }
                            } else if (Math.abs(finalMultiplier - 1.0) > 0.01) {
                                double effectiveMultiplier = finalMultiplier;
                                if (recommendedPosts.containsKey(pId)) {
                                    adjustedByMultiplier++;
                                    recommendedPosts.computeIfPresent(pId, (k, v) -> v * effectiveMultiplier);
                                }
                            }
                        }
                    }
                }
                log.info("强惩罚命中候选 {} 篇（命中后实际剔除 {} 篇）。", strongRejectMatchedCandidates, removedByStrongReject);
                log.info("全局标签调整完成: 强惩罚剔除 {} 篇, 乘法调整 {} 篇。", removedByStrongReject, adjustedByMultiplier);
            }
        }
        if (!recommendedPosts.isEmpty()) {
            log.info("全局调整后 Top{}: {}", GLOBAL_ADJUST_LOG_TOP_N, formatTopScores(recommendedPosts, GLOBAL_ADJUST_LOG_TOP_N));
        }

        log.info("最终打分完成。正在对 {} 篇候选帖子进行排序。", recommendedPosts.size());
        
        List<Long> recommendedPostIds = recommendedPosts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Filter recommended posts by viewed/owned
        List<Long> filteredRecommendedPostIds = recommendedPostIds.stream()
                .filter(id -> !excludedPostIds.contains(id))
                .collect(Collectors.toList());
                
        log.info("过滤已浏览/自己发布后的候选集数量: {}", filteredRecommendedPostIds.size());
        if (filteredRecommendedPostIds.size() > 0) {
             log.info("Top 5 候选帖子ID: {}", 
                filteredRecommendedPostIds.stream().limit(5).collect(Collectors.toList()));
        }

        // FIX: Shuffle top candidates to ensure freshness on refresh (page 1)
        if (page == 1 && !filteredRecommendedPostIds.isEmpty()) {
            int shuffleWindow = Math.min(filteredRecommendedPostIds.size(), 20); // Shuffle top 20
            if (shuffleWindow > 1) {
                // subList returns a view, shuffling it modifies the original list
                Collections.shuffle(filteredRecommendedPostIds.subList(0, shuffleWindow));
            }
        }

        List<Post> finalPosts = new ArrayList<>();
        int recStart = (page - 1) * size;
        int recEnd = recStart + size;

        // (Removed redundant viewedPostIds fetching)

        if (recStart < filteredRecommendedPostIds.size()) {
            int actualEnd = Math.min(recEnd, filteredRecommendedPostIds.size());
            List<Long> subList = filteredRecommendedPostIds.subList(recStart, actualEnd);
            List<Post> recPosts = postMapper.selectPostsWithUserByIds(subList);
            
            // Maintain order and attach recommendation scores
            Map<Long, Post> postMap = recPosts.stream().collect(Collectors.toMap(Post::getId, p -> p));
            for (Long id : subList) {
                if (postMap.containsKey(id)) {
                    Post p = postMap.get(id);
                    // Attach the recommendation score (used by eval endpoint)
                    Double score = recommendedPosts.get(id);
                    if (score != null) {
                        p.setRecommendScore(score);
                    }
                    finalPosts.add(p);
                }
            }
        }

        // --- INJECT FOLLOWED USER POST (PAGE 1 ONLY) ---
        if (page == 1) {
            // Check if we have space or just insert it at top (usually at top or 2nd position)
            // Fetch one recent post from followed users that is NOT viewed and NOT owned
            // Combine excludedPostIds + already selected IDs in finalPosts
            List<Long> currentExcluded = new ArrayList<>(excludedPostIds);
            finalPosts.forEach(p -> currentExcluded.add(p.getId()));

            List<Post> followedPosts = postMapper.selectRecentFollowedPosts(userId, 5, currentExcluded);
            if (!followedPosts.isEmpty()) {
                // Pick one randomly or the latest
                Post injectedPost = followedPosts.get(random.nextInt(followedPosts.size()));
                injectedPost.setIsFollowedAuthor(true); // Flag for frontend red name

                // Insert at position 1 (2nd item) if possible, or 0 if list empty
                if (!finalPosts.isEmpty()) {
                    finalPosts.add(Math.min(1, finalPosts.size()), injectedPost);
                } else {
                    finalPosts.add(injectedPost);
                }
                
                // If we exceeded size, remove last
                if (finalPosts.size() > size) {
                    finalPosts.remove(finalPosts.size() - 1);
                }
            }
        }
        // -----------------------------------------------

        if (finalPosts.size() < size) {
            int needed = size - finalPosts.size();
            log.info("推荐结果不足 ({}/{}). 补充 {} 篇热门帖子。", finalPosts.size(), size, needed);
            // We need to pass excludedPostIds to selectHotPostsWithUser to exclude them as well
            // But selectHotPostsWithUser's 3rd arg is 'excludeIds', we can combine excludedPostIds + already selected IDs
            List<Long> excludeIds = new ArrayList<>(filteredRecommendedPostIds);
            excludeIds.addAll(excludedPostIds);
            
            // Fetch more candidates to allow shuffling for variety
            int candidateCount = Math.max(needed * 3, 20);
            int hotOffset = 0;
            List<Post> hotPosts = postMapper.selectHotPostsWithUser(candidateCount, hotOffset, new ArrayList<>(excludeIds));
            
            // Shuffle to ensure new content on refresh
            Collections.shuffle(hotPosts);
            
            // Take needed amount
            List<Post> selectedHotPosts = hotPosts.stream().limit(needed).collect(Collectors.toList());
            finalPosts.addAll(selectedHotPosts);
        }

        log.info("推荐流程结束。共返回 {} 篇帖子。总耗时: {}ms", finalPosts.size(), (System.currentTimeMillis() - startTime));
        return finalPosts;
    }


    public List<Post> getRecommendations(Long userId) {
        return getRecommendations(userId, 1, RECOMMENDATION_COUNT);
    }

    //已弃用
    private double calculateCosineSimilarity(Map<Long, Double> v1, Map<Long, Double> v2) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (Double val : v1.values()) {
            normA += val * val;
        }
        for (Double val : v2.values()) {
            normB += val * val;
        }

        if (normA == 0 || normB == 0) return 0.0;

        // Dot product: only common keys
        for (Map.Entry<Long, Double> entry : v1.entrySet()) {
            if (v2.containsKey(entry.getKey())) {
                dotProduct += entry.getValue() * v2.get(entry.getKey());
            }
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private boolean isNewUser(Long userId) {
        // Check if user has any interactions
        // Simple check: no likes, no favorites, no comments, no views
        return likeMapper.selectCount(new QueryWrapper<Like>().eq("user_id", userId)) == 0 &&
               favoriteMapper.selectCount(new QueryWrapper<Favorite>().eq("user_id", userId)) == 0 &&
               commentMapper.selectCount(new QueryWrapper<Comment>().eq("user_id", userId)) == 0 &&
               postViewMapper.selectCount(new QueryWrapper<PostView>().eq("user_id", userId)) == 0;
    }

    private String formatTopScores(Map<Long, Double> scores, int topN) {
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> entry.getKey() + ":" + String.format(Locale.ROOT, "%.4f", entry.getValue()))
                .collect(Collectors.joining(", "));
    }
}
