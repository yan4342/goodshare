package yan.goodshare.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import yan.goodshare.entity.UserProfile;
import org.springframework.stereotype.Service;
import yan.goodshare.mapper.UserMapper;
import yan.goodshare.mapper.PostMapper;
import yan.goodshare.mapper.FollowMapper;
import yan.goodshare.entity.User;
import yan.goodshare.entity.Post;
import yan.goodshare.entity.Follow;
//
import yan.goodshare.user.profile.UserProfileUpdateRequest;

import java.util.Optional;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PostMapper postMapper;
    private final FollowMapper followMapper;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, PostMapper postMapper, FollowMapper followMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.postMapper = postMapper;
        this.followMapper = followMapper;
    }

    public Optional<User> findByUsername(String username) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        return Optional.ofNullable(user);
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
                followingCount
        );
    }

    public User updateUserProfile(User user, UserProfileUpdateRequest request) {
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        user.setEmail(request.getEmail());
        user.setBio(request.getBio());
        user.setAvatarUrl(request.getAvatarUrl());
        userMapper.updateById(user);
        return user;
    }
}
