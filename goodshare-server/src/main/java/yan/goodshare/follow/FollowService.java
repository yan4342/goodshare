package yan.goodshare.follow;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import yan.goodshare.entity.Follow;
import yan.goodshare.mapper.FollowMapper;
import yan.goodshare.mapper.UserMapper;
import yan.goodshare.entity.User;

import java.util.List;

import yan.goodshare.service.NotificationService;

@Service
public class FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    public FollowService(FollowMapper followMapper, UserMapper userMapper, NotificationService notificationService) {
        this.followMapper = followMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    public void followUser(Long followedId) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();

        User follower = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (follower == null) {
            throw new RuntimeException("User not found");
        }

        User followed = userMapper.selectById(followedId);
        if (followed == null) {
            throw new RuntimeException("User to follow not found");
        }
        
        if ("system_notification".equals(followed.getUsername())) {
            throw new RuntimeException("Cannot follow system notification user");
        }

        if (follower.getId().equals(followed.getId())) {
            throw new RuntimeException("You cannot follow yourself");
        }

        Follow existingFollow = followMapper.selectOne(new QueryWrapper<Follow>()
                .eq("follower_id", follower.getId())
                .eq("followed_id", followedId));
        if (existingFollow != null) {
            throw new RuntimeException("You are already following this user");
        }

        Follow follow = new Follow();
        follow.setFollowerId(follower.getId());
        follow.setFollowedId(followed.getId());

        followMapper.insert(follow);
        
        // Create notification
        notificationService.createNotification(followed.getId(), follower.getId(), "FOLLOW", null);
    }

    public void unfollowUser(Long followedId) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();

        User follower = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (follower == null) {
            throw new RuntimeException("User not found");
        }

        int deleted = followMapper.delete(new QueryWrapper<Follow>()
                .eq("follower_id", follower.getId())
                .eq("followed_id", followedId));

        if (deleted == 0) {
            throw new RuntimeException("You are not following this user");
        }
    }

    public List<Follow> getFollowing() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();

        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        return followMapper.selectList(new QueryWrapper<Follow>().eq("follower_id", user.getId()));
    }

    public List<Follow> getFollowers() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();

        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        return followMapper.selectList(new QueryWrapper<Follow>().eq("followed_id", user.getId()));
    }

    public long getFollowingCount(Long userId) {
        return followMapper.selectCount(new QueryWrapper<Follow>().eq("follower_id", userId));
    }

    public long getFollowersCount(Long userId) {
        return followMapper.selectCount(new QueryWrapper<Follow>().eq("followed_id", userId));
    }

    public boolean isFollowing(Long followerId, Long followedId) {
        return followMapper.selectCount(new QueryWrapper<Follow>()
                .eq("follower_id", followerId)
                .eq("followed_id", followedId)) > 0;
    }

    public List<User> getFollowingUsers(Long userId) {
        List<Follow> follows = followMapper.selectList(new QueryWrapper<Follow>().eq("follower_id", userId));
        List<Long> followedIds = follows.stream().map(Follow::getFollowedId).toList();
        if (followedIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(followedIds).stream()
                .filter(u -> !"system_notification".equals(u.getUsername()))
                .toList();
    }

    public List<User> getFollowerUsers(Long userId) {
        List<Follow> follows = followMapper.selectList(new QueryWrapper<Follow>().eq("followed_id", userId));
        List<Long> followerIds = follows.stream().map(Follow::getFollowerId).toList();
        if (followerIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(followerIds).stream()
                .filter(u -> !"system_notification".equals(u.getUsername()))
                .toList();
    }
}
