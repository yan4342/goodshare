package yan.goodshare.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

//
import yan.goodshare.user.profile.UserProfileUpdateRequest;

import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;
import yan.goodshare.entity.*;
import yan.goodshare.mapper.*;
import yan.goodshare.post.PostService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PostMapper postMapper;
    private final FollowMapper followMapper;
    private final PostService postService;
    private final LikeMapper likeMapper;
    private final CommentMapper commentMapper;
    private final FavoriteMapper favoriteMapper;
    private final PostViewMapper postViewMapper;
    private final NotificationMapper notificationMapper;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, PostMapper postMapper, 
                       FollowMapper followMapper, PostService postService, LikeMapper likeMapper,
                       CommentMapper commentMapper, FavoriteMapper favoriteMapper, PostViewMapper postViewMapper,
                       NotificationMapper notificationMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.postMapper = postMapper;
        this.followMapper = followMapper;
        this.postService = postService;
        this.likeMapper = likeMapper;
        this.commentMapper = commentMapper;
        this.favoriteMapper = favoriteMapper;
        this.postViewMapper = postViewMapper;
        this.notificationMapper = notificationMapper;
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        // 1. Delete all posts by this user (Cascades to post-related data)
        // We use postService.deletePost to handle image deletion and other cleanup
        // Note: PostService checks permission. We assume this is called by Admin or Self.
        // If called by Admin, SecurityContext must have ROLE_ADMIN.
        java.util.List<Post> posts = postMapper.selectList(new QueryWrapper<Post>().eq("user_id", userId));
        for (Post post : posts) {
            try {
                postService.deletePost(post.getId());
            } catch (Exception e) {
                // Log and continue? Or fail? 
                // If it fails (e.g. permission), we should probably fail.
                // But PostService.deletePost throws RuntimeException if permission denied.
                throw e; 
            }
        }

        // 2. Delete interactions on OTHER posts
        
        // Likes
        likeMapper.delete(new QueryWrapper<Like>().eq("user_id", userId));
        
        // Comments
        commentMapper.delete(new QueryWrapper<Comment>().eq("user_id", userId));
        
        // Favorites
        favoriteMapper.delete(new QueryWrapper<Favorite>().eq("user_id", userId));
        
        // Post Views
        postViewMapper.delete(new QueryWrapper<PostView>().eq("user_id", userId));

        // 3. Delete Follows
        followMapper.delete(new QueryWrapper<Follow>().eq("follower_id", userId));
        followMapper.delete(new QueryWrapper<Follow>().eq("followed_id", userId));

        // 4. Delete Notifications
        notificationMapper.delete(new QueryWrapper<Notification>().eq("recipient_id", userId));
        notificationMapper.delete(new QueryWrapper<Notification>().eq("sender_id", userId));

        // 5. Delete User
        userMapper.deleteById(userId);
    }

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(userMapper.selectOne(new QueryWrapper<User>().eq("username", username)));
    }

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id));
    }

    public User register(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);
        return user;
    }

    public UserProfile getUserProfile(User user) {
        int postCount = Math.toIntExact(postMapper.selectCount(new QueryWrapper<Post>().eq("user_id", user.getId())));
        int followingCount = Math.toIntExact(followMapper.selectCount(new QueryWrapper<Follow>().eq("follower_id", user.getId())));
        int followerCount = Math.toIntExact(followMapper.selectCount(new QueryWrapper<Follow>().eq("followed_id", user.getId())));

        return new UserProfile(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getBio(),
                user.getAvatarUrl(),
                postCount,
                followerCount,
                followingCount,
                user.getLevel() != null ? user.getLevel() : 1,
                user.getExperience() != null ? user.getExperience() : 0,
                user.getActiveStyle() != null ? user.getActiveStyle() : (user.getLevel() != null ? user.getLevel() : 1)
        );
    }

    public User updateUserProfile(User user, UserProfileUpdateRequest request) {
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        user.setEmail(request.getEmail());
        user.setBio(request.getBio());
        user.setAvatarUrl(request.getAvatarUrl());
        if (request.getActiveStyle() != null) {
            user.setActiveStyle(request.getActiveStyle());
        }
        userMapper.updateById(user);
        return user;
    }

    public void addExperience(Long userId, int exp) {
        User user = userMapper.selectById(userId);
        if (user != null) {
            int currentExp = user.getExperience() != null ? user.getExperience() : 0;
            int newExp = currentExp + exp;
            user.setExperience(newExp);
            
            // Calculate new level: e.g. level = sqrt(exp / 10) + 1
            // 0-9: Lv1, 10-39: Lv2, 40-89: Lv3, 90-159: Lv4
            int newLevel = (int) Math.sqrt(newExp / 10.0) + 1;
            if (newLevel > (user.getLevel() != null ? user.getLevel() : 1)) {
                user.setLevel(newLevel);
            }
            userMapper.updateById(user);
        }
    }

    @Transactional
    public User getSystemUser() {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", "system_notification"));
        if (user == null) {
            user = new User();
            user.setUsername("system_notification");
            user.setNickname("系统通知");
            user.setEmail("1601110@qq.com");
            user.setPassword(passwordEncoder.encode("SystemUser_NoLogin_!@#123"));
            user.setRole("USER");
            userMapper.insert(user);
        }
        return user;
    }
}
