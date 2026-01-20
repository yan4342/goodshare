package yan.goodshare.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import yan.goodshare.entity.UserTagWeight;
import yan.goodshare.mapper.UserTagWeightMapper;

import java.util.List;

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

    public List<UserTagWeight> getUserWeights(Long userId) {
        // 1. Get all tags
        List<Tag> allTags = tagMapper.selectList(null);
        
        // 2. Get existing user weights
        List<UserTagWeight> userWeights = userTagWeightMapper.selectByUserId(userId);
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
}
