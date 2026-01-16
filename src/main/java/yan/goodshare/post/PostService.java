package yan.goodshare.post;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import yan.goodshare.entity.Post;
import org.springframework.stereotype.Service;
import yan.goodshare.mapper.PostMapper;
import yan.goodshare.mapper.TagMapper;
import yan.goodshare.mapper.UserMapper;
import yan.goodshare.search.SearchService;
import yan.goodshare.entity.Tag;
import yan.goodshare.entity.User;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PostService {

    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final SearchService searchService;
    private final TagMapper tagMapper;

    public PostService(PostMapper postMapper, UserMapper userMapper, SearchService searchService, TagMapper tagMapper) {
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.searchService = searchService;
        this.tagMapper = tagMapper;
    }

    public Post createPost(PostRequest postRequest) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();

        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        Post post = new Post();
        post.setTitle(postRequest.getTitle());
        post.setContent(postRequest.getContent());
        
        // Handle images
        if (postRequest.getImageUrls() != null && !postRequest.getImageUrls().isEmpty()) {
            try {
                post.setImages(new ObjectMapper().writeValueAsString(postRequest.getImageUrls()));
            } catch (Exception e) {
                throw new RuntimeException("Error processing images", e);
            }
        }

        // Handle coverUrl
        if (postRequest.getCoverUrl() != null && !postRequest.getCoverUrl().isEmpty()) {
            post.setCoverUrl(postRequest.getCoverUrl());
        } else if (postRequest.getImageUrls() != null && !postRequest.getImageUrls().isEmpty()) {
            post.setCoverUrl(postRequest.getImageUrls().get(0));
        } else {
            post.setCoverUrl("https://via.placeholder.com/300x400?text=No+Image");
        }

        post.setUser(user);
        post.setUserId(user.getId());

        if (postRequest.getTags() != null) {
            Set<Tag> tags = new HashSet<>();
            for (String tagName : postRequest.getTags()) {
                Tag tag = tagMapper.selectOne(new QueryWrapper<Tag>().eq("name", tagName));
                if (tag == null) {
                    tag = new Tag();
                    tag.setName(tagName);
                    tagMapper.insert(tag);
                }
                tags.add(tag);
            }
            post.setTags(tags);
        }

        postMapper.insert(post);
        searchService.indexPost(post);
        return post;
    }

    public List<Post> getAllPosts() {
        return postMapper.selectPostsWithUser();
    }

    public Post getPostById(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new RuntimeException("Post not found");
        }
        return post;
    }
}
