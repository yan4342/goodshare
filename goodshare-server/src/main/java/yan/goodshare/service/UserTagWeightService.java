package yan.goodshare.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import yan.goodshare.entity.UserTagWeight;
import yan.goodshare.mapper.UserTagWeightMapper;
import yan.goodshare.mapper.PostMapper;
import yan.goodshare.mapper.AppConfigMapper;
import yan.goodshare.entity.AppConfig;

import java.util.List;
import java.util.Set;

import yan.goodshare.entity.Tag;
import yan.goodshare.mapper.TagMapper;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserTagWeightService {

    @Autowired
    private UserTagWeightMapper userTagWeightMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private AppConfigMapper appConfigMapper;

    private static final double MIN_WEIGHT = 0.5;
    private static final double MAX_WEIGHT = 2.0;
    private static final double DELTA_SCALE = 0.01;
    private static final double DISLIKE_DELTA = -0.08;

    public List<UserTagWeight> getUserWeights(Long userId) {
        // 1. Get all tags
        List<Tag> allTags = tagMapper.selectList(null);
        
        // 2. Get existing user weights
        // Use selectList with QueryWrapper to ensure proper mapping of snake_case columns
        List<UserTagWeight> userWeights = userTagWeightMapper.selectList(new QueryWrapper<UserTagWeight>().eq("user_id", userId));
        Map<Long, UserTagWeight> weightMap = userWeights.stream()
                .collect(Collectors.toMap(UserTagWeight::getTagId, w -> w));
        
        // 3. Merge
        List<UserTagWeight> result = new ArrayList<>();
        for (Tag tag : allTags) {
            if (weightMap.containsKey(tag.getId())) {
                UserTagWeight w = weightMap.get(tag.getId());
                w.setTagName(tag.getName()); // Ensure name is set (though query already sets it)
                result.add(w);
            } else {
                UserTagWeight w = new UserTagWeight();
                w.setUserId(userId);
                w.setTagId(tag.getId());
                w.setTagName(tag.getName());
                w.setWeight(1.0); // Default weight
                result.add(w);
            }
        }
        
        return result;
    }

    public void updateUserWeight(Long userId, Long tagId, Double weight) {
        UserTagWeight existing = userTagWeightMapper.selectOne(new QueryWrapper<UserTagWeight>()
                .eq("user_id", userId)
                .eq("tag_id", tagId));

        if (existing != null) {
            existing.setWeight(weight);
            userTagWeightMapper.updateById(existing);
        } else {
            UserTagWeight newWeight = new UserTagWeight();
            newWeight.setUserId(userId);
            newWeight.setTagId(tagId);
            newWeight.setWeight(weight);
            userTagWeightMapper.insert(newWeight);
        }
    }

    public void applyInteractionWeight(Long userId, Long postId, String weightKey) {
        if (userId == null || postId == null) {
            return;
        }
        Set<Tag> tags = postMapper.selectTagsByPostId(postId);
        if (tags == null || tags.isEmpty()) {
            return;
        }
        double weightValue = getWeightValue(weightKey, 1.0);
        adjustUserTagWeights(userId, tags, weightValue * DELTA_SCALE);
    }

    public void applyDislike(Long userId, Long postId) {
        if (userId == null || postId == null) {
            return;
        }
        Set<Tag> tags = postMapper.selectTagsByPostId(postId);
        if (tags == null || tags.isEmpty()) {
            return;
        }
        adjustUserTagWeights(userId, tags, DISLIKE_DELTA);
    }

    private void adjustUserTagWeights(Long userId, Set<Tag> tags, double delta) {
        for (Tag tag : tags) {
            UserTagWeight existing = userTagWeightMapper.selectOne(new QueryWrapper<UserTagWeight>()
                    .eq("user_id", userId)
                    .eq("tag_id", tag.getId()));
            if (existing == null) {
                UserTagWeight newWeight = new UserTagWeight();
                newWeight.setUserId(userId);
                newWeight.setTagId(tag.getId());
                newWeight.setWeight(clamp(1.0 + delta));
                userTagWeightMapper.insert(newWeight);
            } else {
                existing.setWeight(clamp(existing.getWeight() + delta));
                userTagWeightMapper.updateById(existing);
            }
        }
    }

    private double getWeightValue(String key, double defaultValue) {
        AppConfig config = appConfigMapper.selectOne(new QueryWrapper<AppConfig>().eq("config_key", key));
        if (config == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(config.getConfigValue());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double clamp(double value) {
        return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, value));
    }
}
