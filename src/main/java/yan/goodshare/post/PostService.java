package yan.goodshare.post;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import yan.goodshare.entity.Post;
import org.springframework.stereotype.Service;
import yan.goodshare.mapper.PostMapper;
import yan.goodshare.mapper.PostViewMapper;
import yan.goodshare.mapper.TagMapper;
import yan.goodshare.mapper.UserMapper;
import yan.goodshare.mapper.NotificationMapper;
import yan.goodshare.search.SearchService;
import yan.goodshare.entity.PostView;
import yan.goodshare.entity.Tag;
import yan.goodshare.entity.User;
import yan.goodshare.entity.Notification;

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
    private final PostViewMapper postViewMapper;
    private final NotificationMapper notificationMapper;

    public PostService(PostMapper postMapper, UserMapper userMapper, SearchService searchService, TagMapper tagMapper, ObjectMapper objectMapper, PostViewMapper postViewMapper, NotificationMapper notificationMapper) {
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.searchService = searchService;
        this.tagMapper = tagMapper;
        this.objectMapper = objectMapper;
        this.postViewMapper = postViewMapper;
        this.notificationMapper = notificationMapper;
    }

    @Transactional
    public void recordView(Long postId, String username) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            return;
        }

        // If user is logged in
        if (username != null) {
            User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
            if (user != null) {
                // 1. Check if user is the author
                if (user.getId().equals(post.getUserId())) {
                    return; // Author viewing their own post does not count
                }

                // 2. Check if user has already viewed this post
                Long count = postViewMapper.selectCount(new QueryWrapper<PostView>()
                        .eq("user_id", user.getId())
                        .eq("post_id", postId));
                
                if (count > 0) {
                    return; // Already viewed
                }

                // 3. Record new view
                try {
                    PostView postView = new PostView();
                    postView.setPostId(postId);
                    postView.setUserId(user.getId());
                    postView.setCreatedAt(LocalDateTime.now());
                    postViewMapper.insert(postView);
                } catch (Exception e) {
                    // In case of race condition where duplicate insert fails, stop here
                    return;
                }
            }
        }

        // Increment view count in posts table
        post.setViewCount(post.getViewCount() + 1);
        postMapper.updateById(post);
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
        post.setContent(postRequest.getContent() != null ? postRequest.getContent() : "");
        
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
        
        if (post.getTags() != null) {
            for (Tag tag : post.getTags()) {
                try {
                    postMapper.insertPostTag(post.getId(), tag.getId());
                } catch (Exception e) {
                    // Ignore duplicate key errors if any
                }
            }
        }

        searchService.indexPost(post);
        return post;
    }

    public IPage<Post> getAllPosts(String tag, int page, int size) {
        Page<Post> pageParam = new Page<>(page, size);
        if (tag != null && !tag.isEmpty()) {
            return postMapper.selectPostsByTagNamePage(pageParam, tag);
        }
        return postMapper.selectPostsWithUserPage(pageParam);
    }

    public List<Post> getAllPosts(String tag) {
        if (tag != null && !tag.isEmpty()) {
            return postMapper.selectPostsByTagName(tag);
        }
        return postMapper.selectPostsWithUser();
    }

    public List<Post> getAllPosts() {
        return getAllPosts(null);
    }

    public Post getPostById(Long id) {
        Post post = postMapper.selectPostWithUserByIdIgnoreStatus(id);
        if (post == null) {
            throw new RuntimeException("Post not found");
        }

        // If post is rejected (status == 2), only allow owner or admin to view
        if (post.getStatus() == 2) {
            boolean isAllowed = false;
            try {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                    UserDetails userDetails = (UserDetails) auth.getPrincipal();
                    User currentUser = userMapper.selectOne(new QueryWrapper<User>().eq("username", userDetails.getUsername()));
                    
                    if (currentUser != null) {
                        if (currentUser.getId().equals(post.getUserId())) {
                            isAllowed = true;
                        } else {
                            // Check admin role
                            isAllowed = userDetails.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore auth errors
            }

            if (!isAllowed) {
                throw new RuntimeException("Post not found"); // Hide rejected post
            }
        }

        post.setTags(postMapper.selectTagsByPostId(id));
        return post;
    }

    public List<Post> getPostsByUserId(Long userId) {
        // Check if current user is the requested user
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                UserDetails userDetails = (UserDetails) auth.getPrincipal();
                User currentUser = userMapper.selectOne(new QueryWrapper<User>().eq("username", userDetails.getUsername()));
                
                if (currentUser != null && currentUser.getId().equals(userId)) {
                    // Owner can see all posts including rejected
                    return postMapper.selectPostsByUserIdWithUserIgnoreStatus(userId);
                }
            }
        } catch (Exception e) {
            // Ignore auth errors, fall back to public view
        }
        
        return postMapper.selectPostsByUserIdWithUser(userId);
    }

    @Transactional
    public Post updatePost(Long id, PostRequest postRequest) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new RuntimeException("Post not found");
        }

        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User currentUser = userMapper.selectOne(new QueryWrapper<User>().eq("username", userDetails.getUsername()));
        
        if (currentUser == null || !post.getUserId().equals(currentUser.getId())) {
            throw new RuntimeException("You don't have permission to update this post");
        }

        post.setTitle(postRequest.getTitle());
        post.setContent(postRequest.getContent() != null ? postRequest.getContent() : "");
        
        // Handle images
        if (postRequest.getImageUrls() != null && !postRequest.getImageUrls().isEmpty()) {
            try {
                post.setImages(new ObjectMapper().writeValueAsString(postRequest.getImageUrls()));
            } catch (Exception e) {
                throw new RuntimeException("Error processing images", e);
            }
        } else {
            post.setImages(null);
        }

        // Handle coverUrl
        if (postRequest.getCoverUrl() != null && !postRequest.getCoverUrl().isEmpty()) {
            post.setCoverUrl(postRequest.getCoverUrl());
        } else if (postRequest.getImageUrls() != null && !postRequest.getImageUrls().isEmpty()) {
            post.setCoverUrl(postRequest.getImageUrls().get(0));
        } else {
             post.setCoverUrl(null);
        }

        // Reset status to pending
        post.setStatus(0);
        // Update timestamp if needed, or keep original created_at? 
        // Usually updatedAt should be updated. Assuming there is no updatedAt field in Entity based on previous context, skipping.
        
        // Update tags
        postMapper.deletePostTags(id);
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
            
            for (Tag tag : tags) {
                try {
                    postMapper.insertPostTag(post.getId(), tag.getId());
                } catch (Exception e) {
                    // Ignore duplicate key errors
                }
            }
        }

        postMapper.updateById(post);
        searchService.indexPost(post);
        
        return post;
    }

    @Transactional
    public void deletePosts(List<Long> ids) {
        for (Long id : ids) {
            deletePost(id);
        }
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
        postViewMapper.delete(new QueryWrapper<PostView>().eq("post_id", postId));
        notificationMapper.delete(new QueryWrapper<Notification>()
                .eq("related_id", postId)
                .in("type", "LIKE", "COMMENT"));
        
        // Delete from Elasticsearch
        searchService.deletePost(postId);

        // Finally delete the post
        postMapper.deleteById(postId);
    }

    public IPage<Post> getPendingPosts(int page, int size) {
        Page<Post> pageParam = new Page<>(page, size);
        return postMapper.selectPendingPostsPage(pageParam);
    }

    public void updatePostStatus(Long postId, Integer status) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new RuntimeException("Post not found");
        }
        post.setStatus(status);
        postMapper.updateById(post);
        
        // If rejected (status=2), maybe remove from ES index?
        // If approved (status=1) or pending (status=0), update ES?
        // For simplicity, just re-index if not rejected. If rejected, delete from index.
        if (status == 2) {
            searchService.deletePost(postId);
        } else {
            searchService.indexPost(post);
        }
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
