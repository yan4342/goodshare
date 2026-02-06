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

    public TagService(TagMapper tagMapper, PostMapper postMapper, PostService postService, SearchService searchService) {
        this.tagMapper = tagMapper;
        this.postMapper = postMapper;
        this.postService = postService;
        this.searchService = searchService;
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
