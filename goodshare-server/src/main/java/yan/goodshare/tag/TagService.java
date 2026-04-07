package yan.goodshare.tag;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yan.goodshare.mapper.PostMapper;
import yan.goodshare.mapper.TagMapper;
import yan.goodshare.entity.Tag;
import yan.goodshare.entity.Post;
import yan.goodshare.post.PostService;
import yan.goodshare.search.SearchService;

import java.util.List;

@Service
public class TagService {

    private final TagMapper tagMapper;
    private final PostMapper postMapper;
    private final PostService postService;
    private final SearchService searchService;
    private final yan.goodshare.search.SearchStatsService searchStatsService;

    public TagService(TagMapper tagMapper, PostMapper postMapper, PostService postService, SearchService searchService, yan.goodshare.search.SearchStatsService searchStatsService) {
        this.tagMapper = tagMapper;
        this.postMapper = postMapper;
        this.postService = postService;
        this.searchService = searchService;
        this.searchStatsService = searchStatsService;
    }

    public Tag createTag(String name) {
        Tag existingTag = tagMapper.selectOne(new QueryWrapper<Tag>().eq("name", name));
        if (existingTag != null) {
            throw new RuntimeException("Tag already exists");
        }
        Tag tag = new Tag();
        tag.setName(name);
        tagMapper.insert(tag);
        return tag;
    }

    public List<Tag> getAllTags() {
        return tagMapper.selectList(null);
    }

    public List<java.util.Map<String, Object>> getTrendingTasks() {
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        List<String> seenNames = new java.util.ArrayList<>();
        
        // 1. Get from hot keywords (Search Stats)
        List<yan.goodshare.entity.SearchStats> hotKeywords = searchStatsService.getHotKeywords();
        if (hotKeywords != null) {
            for (yan.goodshare.entity.SearchStats stats : hotKeywords) {
                String name = stats.getKeyword();
                if (name != null && name.length() > 1 && !name.matches("^\\d+$") && !seenNames.contains(name)) {
                    java.util.Map<String, Object> task = new java.util.HashMap<>();
                    task.put("name", name);
                    task.put("type", "hot_word");
                    result.add(task);
                    seenNames.add(name);
                }
                if (result.size() >= 3) {
                    break;
                }
            }
        }

        // 2. Get from trending tags (if we need more)
        if (result.size() < 5) {
            List<java.util.Map<String, Object>> tags = tagMapper.selectTrendingTags(50);
            for (java.util.Map<String, Object> map : tags) {
                String name = (String) map.get("name");
                if (name != null && name.length() > 1 && !name.matches("^\\d+$")) {
                    if (!seenNames.contains(name)) {
                        java.util.Map<String, Object> task = new java.util.HashMap<>();
                        task.put("name", name);
                        task.put("type", "tag");
                        result.add(task);
                        seenNames.add(name);
                    }
                }
                if (result.size() >= 5) {
                    break;
                }
            }
        }
        
        return result;
    }

    @Transactional
    public void deleteTag(Long id) {
        List<Long> onlyTagPostIds = postMapper.selectPostIdsWithOnlyTag(id);
        List<Long> multiTagPostIds = postMapper.selectPostIdsWithMultipleTags(id);

        if (multiTagPostIds != null && !multiTagPostIds.isEmpty()) {
            postMapper.deleteTagFromPosts(id, multiTagPostIds);
            for (Long postId : multiTagPostIds) {
                Post post = postMapper.selectPostWithUserByIdIgnoreStatus(postId);
                if (post != null) {
                    Integer status = post.getStatus() != null ? post.getStatus() : 0;
                    if (status == 2) {
                        searchService.deletePost(postId);
                    } else {
                        searchService.indexPost(post);
                    }
                }
            }
        }

        if (onlyTagPostIds != null && !onlyTagPostIds.isEmpty()) {
            for (Long postId : onlyTagPostIds) {
                postService.deletePostAsAdmin(postId);
            }
        }

        tagMapper.deletePostTagsByTagId(id);
        tagMapper.deleteById(id);
    }
}
