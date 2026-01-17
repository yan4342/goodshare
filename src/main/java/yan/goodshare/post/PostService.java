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

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final SearchService searchService;
    private final TagMapper tagMapper;
    private final ObjectMapper objectMapper;

    public PostService(PostMapper postMapper, UserMapper userMapper, SearchService searchService, TagMapper tagMapper, ObjectMapper objectMapper) {
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.searchService = searchService;
        this.tagMapper = tagMapper;
        this.objectMapper = objectMapper;
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
        }
        // No default placeholder


        post.setUser(user);
        post.setUserId(user.getId());
        post.setCreatedAt(LocalDateTime.now());

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
        Post post = postMapper.selectPostWithUserById(id);
        if (post == null) {
            throw new RuntimeException("Post not found");
        }
        post.setTags(postMapper.selectTagsByPostId(id));
        return post;
    }

    public List<Post> getPostsByUserId(Long userId) {
        return postMapper.selectPostsByUserIdWithUser(userId);
    }

    @Transactional
    public void deletePost(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new RuntimeException("Post not found");
        }

        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User currentUser = userMapper.selectOne(new QueryWrapper<User>().eq("username", userDetails.getUsername()));
        
        if (currentUser == null) {
            throw new RuntimeException("User not found");
        }

        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!post.getUserId().equals(currentUser.getId()) && !isAdmin) {
            throw new RuntimeException("You don't have permission to delete this post");
        }

        // Delete images
        deleteImageFile(post.getCoverUrl());
        if (post.getImages() != null) {
            try {
                List<String> imageUrls = objectMapper.readValue(post.getImages(), List.class);
                for (String url : imageUrls) {
                    deleteImageFile(url);
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        // Delete related data
        postMapper.deletePostTags(postId);
        postMapper.deletePostComments(postId);
        postMapper.deletePostLikes(postId);
        postMapper.deletePostFavorites(postId);
        
        // Finally delete the post
        postMapper.deleteById(postId);
    }

    private void deleteImageFile(String url) {
        if (url != null && url.contains("/uploads/")) {
            String filename = url.substring(url.lastIndexOf("/") + 1);
            try {
                java.nio.file.Path path = java.nio.file.Paths.get("uploads").resolve(filename);
                java.nio.file.Files.deleteIfExists(path);
            } catch (java.io.IOException e) {
                // Ignore deletion errors
            }
        }
    }
}
