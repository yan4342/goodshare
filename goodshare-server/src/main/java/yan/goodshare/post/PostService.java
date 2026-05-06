package yan.goodshare.post;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import yan.goodshare.entity.Post;
import org.springframework.stereotype.Service;
import yan.goodshare.entity.AppConfig;
import yan.goodshare.mapper.PostMapper;
import yan.goodshare.mapper.PostViewMapper;
import yan.goodshare.mapper.TagMapper;
import yan.goodshare.mapper.UserMapper;
import yan.goodshare.mapper.NotificationMapper;
import yan.goodshare.mapper.AppConfigMapper;
import yan.goodshare.search.SearchService;
import yan.goodshare.entity.PostView;
import yan.goodshare.entity.Tag;
import yan.goodshare.entity.User;
import yan.goodshare.entity.Notification;
import yan.goodshare.service.UserTagWeightService;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final SearchService searchService;
    private final TagMapper tagMapper;
    private final ObjectMapper objectMapper;
    private final PostViewMapper postViewMapper;
    private final NotificationMapper notificationMapper;
    private final UserTagWeightService userTagWeightService;
    private final AppConfigMapper appConfigMapper;
    private final yan.goodshare.search.SearchStatsService searchStatsService;

    public PostService(PostMapper postMapper, UserMapper userMapper, SearchService searchService, TagMapper tagMapper, ObjectMapper objectMapper, PostViewMapper postViewMapper, NotificationMapper notificationMapper, UserTagWeightService userTagWeightService, AppConfigMapper appConfigMapper, yan.goodshare.search.SearchStatsService searchStatsService) {
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.searchService = searchService;
        this.tagMapper = tagMapper;
        this.objectMapper = objectMapper;
        this.postViewMapper = postViewMapper;
        this.notificationMapper = notificationMapper;
        this.userTagWeightService = userTagWeightService;
        this.appConfigMapper = appConfigMapper;
        this.searchStatsService = searchStatsService;
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
                    userTagWeightService.applyInteractionWeight(user.getId(), postId, "weight.view");
                } catch (Exception e) {
                    // In case of race condition where duplicate insert fails, stop here
                    return;
                }
            }
        }

        // Increment view count in posts table
        post.setViewCount(post.getViewCount() + 1);
        postMapper.updateById(post);

        // Update Search Index
        try {
            Post fullPost = postMapper.selectPostWithUserByIdIgnoreStatus(postId);
            if (fullPost != null) {
                searchService.indexPost(fullPost);
            }
        } catch (Exception e) {
            System.err.println("Failed to update search index for post " + postId + ": " + e.getMessage());
        }
    }

    public void dislikePost(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new RuntimeException("Post not found");
        }
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        if (user.getId().equals(post.getUserId())) {
            return;
        }
        userTagWeightService.applyDislike(user.getId(), postId);
    }

    public Post createPost(PostRequest postRequest) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();

        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        validatePostContent(postRequest.getTitle(), postRequest.getContent());

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
        
        boolean hasTrendingTask = false;
        List<String> trendingTasks = new java.util.ArrayList<>();
        
        // Fetch hot search keywords
        List<yan.goodshare.entity.SearchStats> hotKeywords = searchStatsService.getHotKeywords();
        if (hotKeywords != null) {
            for (yan.goodshare.entity.SearchStats stats : hotKeywords) {
                String name = stats.getKeyword();
                if (name != null && name.length() > 1 && !name.matches("^\\d+$") && !trendingTasks.contains(name)) {
                    trendingTasks.add(name);
                }
                if (trendingTasks.size() >= 3) break;
            }
        }
        
        // 任务模块：如果热搜关键词不足5个，再补充一些近期热门标签（不包含纯数字且长度大于1的标签）
        if (trendingTasks.size() < 5) {
            List<java.util.Map<String, Object>> trendingTagsRaw = tagMapper.selectTrendingTags(50);
            for (java.util.Map<String, Object> map : trendingTagsRaw) {
                String name = (String) map.get("name");
                if (name != null && name.length() > 1 && !name.matches("^\\d+$")) {
                    if (!trendingTasks.contains(name)) {
                        trendingTasks.add(name);
                    }
                }
                if (trendingTasks.size() >= 5) {
                    break;
                }
            }
        }

        // Check if post title or content contains any trending task word directly
        String fullText = (post.getTitle() + " " + post.getContent()).toLowerCase();
        for (String task : trendingTasks) {
            if (fullText.contains(task.toLowerCase())) {
                hasTrendingTask = true;
                break;
            }
        }

        if (post.getTags() != null) {
            for (Tag tag : post.getTags()) {
                try {
                    postMapper.insertPostTag(post.getId(), tag.getId());
                    if (trendingTasks.contains(tag.getName())) {
                        hasTrendingTask = true;
                    }
                } catch (Exception e) {
                    // Ignore duplicate key errors if any
                }
            }
        }

        if (hasTrendingTask) {
            // Add experience for completing trending task
            int expGain = 10;
            int currentExp = user.getExperience() != null ? user.getExperience() : 0;
            int newExp = currentExp + expGain;
            user.setExperience(newExp);
            int newLevel = (int) Math.sqrt(newExp / 10.0) + 1;
            if (newLevel > (user.getLevel() != null ? user.getLevel() : 1)) {
                user.setLevel(newLevel);
            }
            userMapper.updateById(user);
        }

        searchService.indexPost(post);
        return post;
    }
// 获取帖子列表，支持标签过滤和分页
    public IPage<Post> getAllPosts(String tag, int page, int size) {
        Page<Post> pageParam = new Page<>(page, size);
        IPage<Post> result;
        if (tag != null && !tag.isEmpty()) {
            result = postMapper.selectPostsByTagNamePage(pageParam, tag);
        } else {
            result = postMapper.selectPostsWithUserPage(pageParam);
        }
        loadTagsForPosts(result.getRecords());
        return result;
    }

    public IPage<Post> searchAdminPosts(String keyword, Page<Post> pageParam) {
        IPage<Post> result = postMapper.selectAdminPostsByKeyword(pageParam, keyword);
        loadTagsForPosts(result.getRecords());
        return result;
    }

// 获取帖子列表，不分页，支持标签过滤
    public List<Post> getAllPosts(String tag) {
        if (tag != null && !tag.isEmpty()) {
            return postMapper.selectPostsByTagName(tag);
        }
        return postMapper.selectPostsWithUser();
    }
// 获取所有帖子用于管理员后台，包含待审核和被拒绝的帖子，已弃用
    public List<Post> getAllPosts() {
        List<Post> posts = getAllPosts(null);
        loadTagsForPosts(posts);
        return posts;
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

    public IPage<Post> getPostsByUserId(Long userId, int page, int size) {
        Page<Post> pageParam = new Page<>(page, size);
        // Check if current user is the requested user
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                UserDetails userDetails = (UserDetails) auth.getPrincipal();
                User currentUser = userMapper.selectOne(new QueryWrapper<User>().eq("username", userDetails.getUsername()));
                
                if (currentUser != null && currentUser.getId().equals(userId)) {
                    // Owner can see all posts including rejected
                    return postMapper.selectPostsByUserIdWithUserIgnoreStatus(pageParam, userId);
                }
            }
        } catch (Exception e) {
            // Ignore auth errors, fall back to public view
        }

        return postMapper.selectPostsByUserIdWithUser(pageParam, userId);
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

        validatePostContent(postRequest.getTitle(), postRequest.getContent());

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
        deletePostInternal(postId, true);
    }

    @Transactional
    public void deletePostAsAdmin(Long postId) {
        deletePostInternal(postId, false);
    }

    private void deletePostInternal(Long postId, boolean enforcePermission) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new RuntimeException("Post not found");
        }

        if (enforcePermission) {
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
        IPage<Post> result = postMapper.selectPendingPostsPage(pageParam);
        loadTagsForPosts(result.getRecords());
        return result;
    }

    public IPage<Post> getFollowedPosts(Long userId, int page, int size) {
        Page<Post> pageParam = new Page<>(page, size);
        return postMapper.selectFollowedPostsPage(pageParam, userId);
    }

    public IPage<Post> getHistoryPosts(Long userId, int page, int size) {
        Page<Post> pageParam = new Page<>(page, size);
        return postMapper.selectHistoryPostsPage(pageParam, userId);
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

    public void reindexAllPosts() {
        // Clear all existing data in Elasticsearch to ensure consistency
        searchService.deleteAllPosts();
        
        List<Post> posts = postMapper.selectAllPostsWithUser();
        
        // Filter out rejected posts (status 2) and handle null status
        List<Post> validPosts = posts.stream()
            .filter(post -> {
                Integer status = post.getStatus() != null ? post.getStatus() : 0;
                post.setStatus(status); // Ensure status is set
                return status != 2;
            })
            .collect(java.util.stream.Collectors.toList());
            
        if (!validPosts.isEmpty()) {
            searchService.indexPosts(validPosts);
        }
    }

    private void deleteImageFile(String url) {
        if (url != null && url.contains("/uploads/")) {
            String filename = url.substring(url.lastIndexOf("/") + 1);
            try {
                deleteFileByName(filename);
                String thumbFilename = getThumbFilename(filename);
                if (!thumbFilename.equals(filename)) {
                    deleteFileByName(thumbFilename);
                }
                String originalFromThumb = getOriginalFilename(filename);
                if (originalFromThumb != null && !originalFromThumb.equals(filename)) {
                    deleteFileByName(originalFromThumb);
                }
            } catch (java.io.IOException e) {
                // Ignore deletion errors
            }
        }
    }

    private void deleteFileByName(String filename) throws java.io.IOException {
        java.nio.file.Path path = java.nio.file.Paths.get("uploads").resolve(filename);
        java.nio.file.Files.deleteIfExists(path);
    }

    private String getThumbFilename(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1) return filename + "_thumb";
        return filename.substring(0, dotIndex) + "_thumb" + filename.substring(dotIndex);
    }

    private String getOriginalFilename(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1) {
            if (filename.endsWith("_thumb")) {
                return filename.substring(0, filename.length() - 6);
            }
            return null;
        }
        String base = filename.substring(0, dotIndex);
        if (base.endsWith("_thumb")) {
            return base.substring(0, base.length() - 6) + filename.substring(dotIndex);
        }
        return null;
    }

    private void validatePostContent(String title, String content) {
        String matchedWord = findMatchedForbiddenWord(title, content);
        if (matchedWord != null) {
            throw new RuntimeException("帖子标题或正文包含违禁词：" + matchedWord);
        }
    }

    private String findMatchedForbiddenWord(String title, String content) {
        String normalizedTitle = normalizeText(title);
        String normalizedContent = normalizeText(content);
        for (String forbiddenWord : getForbiddenWords()) {
            String normalizedForbiddenWord = normalizeText(forbiddenWord);
            if (!normalizedForbiddenWord.isEmpty() &&
                    (normalizedTitle.contains(normalizedForbiddenWord) || normalizedContent.contains(normalizedForbiddenWord))) {
                return forbiddenWord;
            }
        }
        return null;
    }

    private List<String> getForbiddenWords() {
        AppConfig config = appConfigMapper.selectOne(new QueryWrapper<AppConfig>().eq("config_key", "post.forbidden_words"));
        String configValue = config != null ? config.getConfigValue() : "毒品,枪支,赌博,嫖娼,诈骗";
        return Arrays.stream(configValue.split("[,，;；\\r\\n]+"))
                .map(String::trim)
                .filter(word -> !word.isEmpty())
                .toList();
    }

    private String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return Jsoup.parse(text)
                .text()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    private void loadTagsForPosts(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return;
        }

        List<Long> postIds = posts.stream().map(Post::getId).collect(Collectors.toList());
        List<java.util.Map<String, Object>> tagMaps = postMapper.selectTagsByPostIds(postIds);

        java.util.Map<Long, Set<Tag>> postTagsMap = new java.util.HashMap<>();
        for (java.util.Map<String, Object> map : tagMaps) {
            Long postId = ((Number) map.get("post_id")).longValue();
            Tag tag = new Tag();
            tag.setId(((Number) map.get("id")).longValue());
            tag.setName((String) map.get("name"));

            postTagsMap.computeIfAbsent(postId, k -> new HashSet<>()).add(tag);
        }

        for (Post post : posts) {
            Set<Tag> tags = postTagsMap.get(post.getId());
            post.setTags(tags != null ? tags : new HashSet<>());
        }
    }
}
