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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

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
        System.out.println("--- Starting Recommendation for User ID: " + userId + " ---");
        // 1. Fetch all interactions
        List<Like> likes = likeMapper.selectList(null);
        List<Favorite> favorites = favoriteMapper.selectList(null);
        List<Comment> comments = commentMapper.selectList(null);
        List<PostView> views = postViewMapper.selectList(null);

        // 2. Build User-Item Matrix
        // Map<UserId, Map<PostId, Weight>>
        Map<Long, Map<Long, Double>> userItemMatrix = new HashMap<>();

        for (PostView view : views) {
            userItemMatrix.computeIfAbsent(view.getUserId(), k -> new HashMap<>())
                    .merge(view.getPostId(), weights.getOrDefault("weight.view", 0.5), Double::sum);
        }
        for (Like like : likes) {
            userItemMatrix.computeIfAbsent(like.getUserId(), k -> new HashMap<>())
                    .merge(like.getPostId(), weights.getOrDefault("weight.like", 1.0), Double::sum);
        }
        for (Favorite favorite : favorites) {
            userItemMatrix.computeIfAbsent(favorite.getUserId(), k -> new HashMap<>())
                    .merge(favorite.getPostId(), weights.getOrDefault("weight.favorite", 2.0), Double::sum);
        }
        for (Comment comment : comments) {
            userItemMatrix.computeIfAbsent(comment.getUserId(), k -> new HashMap<>())
                    .merge(comment.getPostId(), weights.getOrDefault("weight.comment", 3.0), Double::sum);
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
                        recommendedPosts.merge(postId, entry.getValue() * similarity, Double::sum);
                    }
                }
            }
        }

        // 4. Partition Recommendation (Tag-based Boosting)
        List<UserTagWeight> userWeights = userTagWeightMapper.selectByUserId(userId);
        if (userWeights != null && !userWeights.isEmpty()) {
            for (UserTagWeight uw : userWeights) {
                // Only consider weights > 1.0 as "Boost" (1.0 is default/neutral)
                // Or if weight < 1.0, we could penalize, but let's stick to boosting for now.
                // Let's assume user sets weight between 0.0 and 2.0 (or higher).
                if (uw.getWeight() > 1.0) {
                    // Fetch recent posts for this tag (Top 10)
                    List<Post> tagPosts = postMapper.selectPostsByTagId(uw.getTagId(), 10);
                    for (Post p : tagPosts) {
                        // Filter out items already interacted by target user
                        if (!targetUserInteractions.containsKey(p.getId())) {
                            // Boost score: (Weight - 1.0) * BaseMultiplier (e.g. 2.0)
                            // If weight is 2.0, boost is 2.0. If weight is 1.5, boost is 1.0.
                            double boost = (uw.getWeight() - 1.0) * 2.0;
                            recommendedPosts.merge(p.getId(), boost, Double::sum);
                        }
                    }
                }
            }
        }

        // 5. Content-Based Recommendation (Elasticsearch MoreLikeThis)
        // Select top 5 interacted posts as seeds
        List<Long> topInteractedPosts = targetUserInteractions.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(5)
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
                            recommendedPosts.merge(similarId, boost, Double::sum);
                        }
                    } catch (NumberFormatException e) {
                        // Ignore invalid IDs
                    }
                }
            } catch (Exception e) {
                System.err.println("CB Recommendation failed for post " + seedId + ": " + e.getMessage());
            }
        }

        List<Long> recommendedPostIds = recommendedPosts.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<Post> finalPosts = new ArrayList<>();
        int recStart = (page - 1) * size;
        int recEnd = recStart + size;

        if (recStart < recommendedPostIds.size()) {
            int actualEnd = Math.min(recEnd, recommendedPostIds.size());
            List<Long> subList = recommendedPostIds.subList(recStart, actualEnd);
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
            int hotOffset = Math.max(0, recStart - recommendedPostIds.size());
            List<Post> hotPosts = postMapper.selectHotPostsWithUser(needed, hotOffset, recommendedPostIds);
            finalPosts.addAll(hotPosts);
        }

        return finalPosts;
    }

    private List<Post> getRecentPostsPage(int page, int size) {
        return postMapper.selectPostsWithUserPage(new Page<>(page, size)).getRecords();
    }

    public List<Post> getRecommendations(Long userId) {
        return getRecommendations(userId, 1, RECOMMENDATION_COUNT);
    }

    private List<Post> getRandomPosts(int count) {
        return postMapper.selectRecentPostsWithUser(count);
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
}
