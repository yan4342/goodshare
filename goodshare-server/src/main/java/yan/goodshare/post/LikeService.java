package yan.goodshare.post;

import yan.goodshare.entity.Like;
import yan.goodshare.entity.Post;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import yan.goodshare.mapper.LikeMapper;
import yan.goodshare.mapper.PostMapper;
import yan.goodshare.mapper.UserMapper;
import yan.goodshare.entity.User;
import yan.goodshare.search.SearchService;

import yan.goodshare.service.NotificationService;
import yan.goodshare.service.UserTagWeightService;

import java.util.List;

@Service
public class LikeService {

    private final LikeMapper likeMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final SearchService searchService;
    private final UserTagWeightService userTagWeightService;

    public LikeService(LikeMapper likeMapper, PostMapper postMapper, UserMapper userMapper, NotificationService notificationService, SearchService searchService, UserTagWeightService userTagWeightService) {
        this.likeMapper = likeMapper;
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
        this.searchService = searchService;
        this.userTagWeightService = userTagWeightService;
    }

    public void likePost(Long postId) {
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

        Like existingLike = likeMapper.selectOne(new QueryWrapper<Like>()
                .eq("user_id", user.getId())
                .eq("post_id", postId));
        if (existingLike != null) {
            throw new RuntimeException("You have already liked this post");
        }

        Like like = new Like();
        like.setUserId(user.getId());
        like.setPostId(postId);

        likeMapper.insert(like);

        userTagWeightService.applyInteractionWeight(user.getId(), postId, "weight.like");
        
        // Create notification
        notificationService.createNotification(post.getUserId(), user.getId(), "LIKE", postId);
        
        // Update ES index
        searchService.updateLikeCount(postId, (int) getLikeCount(postId));
    }

    public void unlikePost(Long postId) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();

        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        int deleted = likeMapper.delete(new QueryWrapper<Like>()
                .eq("user_id", user.getId())
                .eq("post_id", postId));

        if (deleted == 0) {
            throw new RuntimeException("You have not liked this post");
        }
        
        // Update ES index
        searchService.updateLikeCount(postId, (int) getLikeCount(postId));
    }

    public long getLikeCount(Long postId) {
        return likeMapper.selectCount(new QueryWrapper<Like>().eq("post_id", postId));
    }

    public boolean hasLiked(Long postId) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            String username = userDetails.getUsername();

            User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
            if (user == null) {
                return false;
            }

            return likeMapper.exists(new QueryWrapper<Like>()
                    .eq("user_id", user.getId())
                    .eq("post_id", postId));
        } catch (Exception e) {
            return false;
        }
    }

    public IPage<Post> getUserLikedPosts(int page, int size) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();

        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        return likeMapper.selectLikedPostsPage(new Page<>(page, size), user.getId());
    }
}
