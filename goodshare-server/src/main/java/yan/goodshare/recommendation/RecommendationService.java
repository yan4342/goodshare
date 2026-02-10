package yan.goodshare.recommendation;

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

    private static final int NEIGHBOR_COUNT = 5;
    private static final int RECOMMENDATION_COUNT = 10;
    private static final int DATA_LIMIT = 1000; // Limit interactions to recent 1000 for performance

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
                    System.err.println("Invalid weight value for " + config.getConfigKey() + ": " + config.getConfigValue());
                }
            }
            // Ensure defaults
            weights.putIfAbsent("weight.view", 0.5);
            weights.putIfAbsent("weight.like", 1.0);
            weights.putIfAbsent("weight.favorite", 2.0);
            weights.putIfAbsent("weight.comment", 3.0);
            weights.putIfAbsent("weight.content", 1.0);
            weights.putIfAbsent("weight.comment_count", 0.1);
        } catch (Exception e) {
            System.err.println("Failed to load weights from DB: " + e.getMessage());
            // Fallback defaults
            weights.put("weight.view", 0.5);
            weights.put("weight.like", 1.0);
            weights.put("weight.favorite", 2.0);
            weights.put("weight.comment", 3.0);
            weights.put("weight.content", 1.0);
            weights.put("weight.comment_count", 0.1);
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
        long startTime = System.currentTimeMillis();

        // Optimize: Fetch all viewed post IDs by this user first
        Set<Long> excludedPostIds = new HashSet<>();
        
        // 1. Fetch Viewed Posts
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
            System.out.println("New User detected (no interactions). Returning Hot Posts.");
            int offset = (page - 1) * size;
            // FIX: Pass excludedPostIds to exclude watched/owned content
            return postMapper.selectHotPostsWithUser(size, offset, new ArrayList<>(excludedPostIds));
        }

        // 1. Fetch recent interactions (Limit to avoid OOM and Timeouts)
        // Note: For UserCF, we ideally need the target user's full history, but for performance we limit all.
        // A better approach would be: All target user's + Recent others'. For now, simple limit.
        List<Like> likes = likeMapper.selectList(new QueryWrapper<Like>().orderByDesc("id").last("LIMIT " + DATA_LIMIT));
        List<Favorite> favorites = favoriteMapper.selectList(new QueryWrapper<Favorite>().orderByDesc("id").last("LIMIT " + DATA_LIMIT));
        List<Comment> comments = commentMapper.selectList(new QueryWrapper<Comment>().orderByDesc("id").last("LIMIT " + DATA_LIMIT));
        List<PostView> views = postViewMapper.selectList(new QueryWrapper<PostView>().orderByDesc("id").last("LIMIT " + DATA_LIMIT));

        System.out.println("Data fetch took: " + (System.currentTimeMillis() - startTime) + "ms");
        
        // 2. Build User-Item Matrix
        // Map<UserId, Map<PostId, Weight>>
        Map<Long, Map<Long, Double>> userItemMatrix = new HashMap<>();

        for (PostView view : views) {
            userItemMatrix.computeIfAbsent(view.getUserId(), k -> new HashMap<>())
                    .merge(view.getPostId(), weights.getOrDefault("weight.view", 0.5), (a, b) -> a + b);
        }
        for (Like like : likes) {
            userItemMatrix.computeIfAbsent(like.getUserId(), k -> new HashMap<>())
                    .merge(like.getPostId(), weights.getOrDefault("weight.like", 1.0), (a, b) -> a + b);
        }
        for (Favorite favorite : favorites) {
            userItemMatrix.computeIfAbsent(favorite.getUserId(), k -> new HashMap<>())
                    .merge(favorite.getPostId(), weights.getOrDefault("weight.favorite", 2.0), (a, b) -> a + b);
        }
        for (Comment comment : comments) {
            userItemMatrix.computeIfAbsent(comment.getUserId(), k -> new HashMap<>())
                    .merge(comment.getPostId(), weights.getOrDefault("weight.comment", 3.0), (a, b) -> a + b);
        }

        Map<Long, Double> targetUserInteractions = userItemMatrix.getOrDefault(userId, new HashMap<>());
        Map<Long, Double> recommendedPosts = new HashMap<>();

        // 3. Collaborative Filtering (Python Microservice)
        try {
            // Call Python Service
            String url = "http://localhost:5000/recommend?user_id=" + userId + "&limit=50";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                null, 
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            List<Map<String, Object>> cfRecs = response.getBody();
            if (cfRecs != null) {
                for (Map<String, Object> rec : cfRecs) {
                    Long postId = ((Number) rec.get("post_id")).longValue();
                    Double score = ((Number) rec.get("score")).doubleValue();
                    
                    // Filter out items already interacted by target user
                    if (!targetUserInteractions.containsKey(postId)) {
                        recommendedPosts.merge(postId, score, (a, b) -> a + b);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Python Recommendation Service failed: " + e.getMessage());
            // Fallback: If Python service fails, we might want to use the local logic or just skip CF
            // For now, we log and skip, relying on Hot Posts fallback later.
        }

        /* Legacy Java UserCF Implementation (Replaced by Python Service)
        if (!targetUserInteractions.isEmpty()) {
             // ... (Original Java logic removed/commented)
        }
        */

        // 4. Partition Recommendation (Tag-based Boosting)
        // Use selectList directly to ensure proper mapping (avoid custom query potential mapping issues)
        List<UserTagWeight> userWeights = userTagWeightMapper.selectList(new QueryWrapper<UserTagWeight>().eq("user_id", userId));

        if (userWeights != null && !userWeights.isEmpty()) {
            for (UserTagWeight uw : userWeights) {
                // Only consider weights != 1.0 (1.0 is default/neutral)
                if (Math.abs(uw.getWeight() - 1.0) > 0.01) {
                    // Fetch recent posts for this tag (Fetch more to allow randomization)
                    List<Post> tagPosts = postMapper.selectPostsByTagId(uw.getTagId(), 50);
                    Collections.shuffle(tagPosts); // Randomize to ensure variety on refresh
                    
                    // Limit to 10 after shuffle
                    tagPosts = tagPosts.stream().limit(10).collect(Collectors.toList());
                    
                    for (Post p : tagPosts) {
                        // Filter out items already interacted by target user
                        if (!targetUserInteractions.containsKey(p.getId())) {
                            // Boost/Penalize score: (Weight - 1.0) * BaseMultiplier (e.g. 2.0)
                            double boost = (uw.getWeight() - 1.0) * 2.0;
                            double randomNoise = random.nextDouble() * 0.01; // Small noise to break ties

                            if (boost > 0) {
                                // Positive boost: Add or increase score
                                recommendedPosts.merge(p.getId(), boost + randomNoise, (a, b) -> a + b);
                            } else {
                                // Negative boost: Only penalize if already recommended (don't recommend purely negative items)
                                if (recommendedPosts.containsKey(p.getId())) {
                                    recommendedPosts.merge(p.getId(), boost, (a, b) -> a + b);
                                    // Remove if score drops below zero
                                    if (recommendedPosts.get(p.getId()) < 0) {
                                        recommendedPosts.remove(p.getId());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Content-Based Recommendation (Elasticsearch MoreLikeThis)
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
                        if (!targetUserInteractions.containsKey(similarId) && !similarId.equals(seedId)) {
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
                System.err.println("CB Recommendation failed for post " + seedId + ": " + e.getMessage());
            }
        }

        // 5.5 Comment Count Boosting (Popularity)
        if (!recommendedPosts.isEmpty()) {
            List<Long> candidateIds = new ArrayList<>(recommendedPosts.keySet());
            // Batch fetch
            int batchSize = 100;
            double commentWeight = weights.getOrDefault("weight.comment_count", 0.1);
            
            if (commentWeight > 0.001) {
                for (int i = 0; i < candidateIds.size(); i += batchSize) {
                    int end = Math.min(candidateIds.size(), i + batchSize);
                    List<Long> batchIds = candidateIds.subList(i, end);
                    
                    List<Map<String, Object>> counts = postMapper.selectCommentCountsByPostIds(batchIds);
                    
                    for (Map<String, Object> row : counts) {
                        Long pId = ((Number) row.get("post_id")).longValue();
                        Long count = ((Number) row.get("comment_count")).longValue();
                        
                        if (count > 0) {
                            // Logarithmic boost to prevent runaway scores for viral posts
                            double boost = Math.log1p(count) * commentWeight;
                             recommendedPosts.merge(pId, boost, (a, b) -> a + b);
                        }
                    }
                }
            }
        }

        // 6. Global Weight Adjustment (Apply Tag Penalties/Boosts to ALL candidates)
        if (userWeights != null && !userWeights.isEmpty() && !recommendedPosts.isEmpty()) {
            // Filter weights that are significantly different from 1.0
            Map<Long, Double> activeTagWeights = new HashMap<>();
            for (UserTagWeight uw : userWeights) {
                if (Math.abs(uw.getWeight() - 1.0) > 0.01) {
                    activeTagWeights.put(uw.getTagId(), uw.getWeight());
                }
            }

            if (!activeTagWeights.isEmpty()) {
                List<Long> candidateIds = new ArrayList<>(recommendedPosts.keySet());
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
                                    if (w < 0.1) {
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
                                recommendedPosts.remove(pId);
                            } else if (Math.abs(finalMultiplier - 1.0) > 0.01) {
                                double effectiveMultiplier = finalMultiplier;
                                recommendedPosts.computeIfPresent(pId, (k, v) -> v * effectiveMultiplier);
                            }
                        }
                    }
                }
            }
        }

        List<Long> recommendedPostIds = recommendedPosts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Filter recommended posts by viewed/owned
        List<Long> filteredRecommendedPostIds = recommendedPostIds.stream()
                .filter(id -> !excludedPostIds.contains(id))
                .collect(Collectors.toList());

        // FIX: Shuffle top candidates to ensure freshness on refresh (page 1)
        if (page == 1 && !filteredRecommendedPostIds.isEmpty()) {
            int shuffleWindow = Math.min(filteredRecommendedPostIds.size(), 30); // Shuffle top 30
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
            
            // Maintain order
            Map<Long, Post> postMap = recPosts.stream().collect(Collectors.toMap(Post::getId, p -> p));
            for (Long id : subList) {
                if (postMap.containsKey(id)) {
                    finalPosts.add(postMap.get(id));
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
            System.out.println("Recommendations insufficient (" + finalPosts.size() + "/" + size + "). Filling with " + needed + " hot posts.");
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

        return finalPosts;
    }


    public List<Post> getRecommendations(Long userId) {
        return getRecommendations(userId, 1, RECOMMENDATION_COUNT);
    }

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
}
