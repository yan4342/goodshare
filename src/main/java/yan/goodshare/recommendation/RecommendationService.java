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

@Service
public class RecommendationService {

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
        } catch (Exception e) {
            System.err.println("Failed to load weights from DB: " + e.getMessage());
            // Fallback defaults
            weights.put("weight.view", 0.5);
            weights.put("weight.like", 1.0);
            weights.put("weight.favorite", 2.0);
            weights.put("weight.comment", 3.0);
            weights.put("weight.content", 1.0);
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

        // 0. New User / Cold Start Check
        // If user has no interactions, directly recommend hot posts to avoid unnecessary computation
        if (isNewUser(userId)) {
            System.out.println("New User detected (no interactions). Returning Hot Posts.");
            int offset = (page - 1) * size;
            return postMapper.selectHotPostsWithUser(size, offset, Collections.emptyList());
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

        // 3. UserCF Recommendation
        if (!targetUserInteractions.isEmpty()) {
            // Calculate Similarity (Cosine Similarity)
            Map<Long, Double> userSimilarities = new HashMap<>();
            for (Map.Entry<Long, Map<Long, Double>> entry : userItemMatrix.entrySet()) {
                Long otherUserId = entry.getKey();
                if (otherUserId.equals(userId)) continue;

                double similarity = calculateCosineSimilarity(targetUserInteractions, entry.getValue());
                if (similarity > 0) {
                    userSimilarities.put(otherUserId, similarity);
                }
            }

            // Find Top K Neighbors
            List<Long> nearestNeighbors = userSimilarities.entrySet().stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                    .limit(NEIGHBOR_COUNT)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            // Recommend Items from Neighbors
            for (Long neighborId : nearestNeighbors) {
                double similarity = userSimilarities.get(neighborId);
                Map<Long, Double> neighborInteractions = userItemMatrix.get(neighborId);

                for (Map.Entry<Long, Double> entry : neighborInteractions.entrySet()) {
                    Long postId = entry.getKey();
                    // Filter out items already interacted by target user
                    if (!targetUserInteractions.containsKey(postId)) {
                        double randomNoise = random.nextDouble() * 0.01;
                        recommendedPosts.merge(postId, entry.getValue() * similarity + randomNoise, (a, b) -> a + b);
                    }
                }
            }
        }

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

        List<Post> finalPosts = new ArrayList<>();
        int recStart = (page - 1) * size;
        int recEnd = recStart + size;

        // Collect all viewed post IDs by this user to filter them out from final results
        Set<Long> viewedPostIds = new HashSet<>();
        List<PostView> allUserViews = postViewMapper.selectList(new QueryWrapper<PostView>().eq("user_id", userId));
        for (PostView v : allUserViews) {
            viewedPostIds.add(v.getPostId());
        }

        // Filter recommended posts by viewed
        List<Long> filteredRecommendedPostIds = recommendedPostIds.stream()
                .filter(id -> !viewedPostIds.contains(id))
                .collect(Collectors.toList());

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

        if (finalPosts.size() < size) {
            int needed = size - finalPosts.size();
            System.out.println("Recommendations insufficient (" + finalPosts.size() + "/" + size + "). Filling with " + needed + " hot posts.");
            // We need to pass viewedPostIds to selectHotPostsWithUser to exclude them as well
            // But selectHotPostsWithUser's 3rd arg is 'excludeIds', we can combine viewedPostIds + already selected IDs
            List<Long> excludeIds = new ArrayList<>(filteredRecommendedPostIds);
            excludeIds.addAll(viewedPostIds);
            
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
