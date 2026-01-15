package yan.goodshare.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import yan.goodshare.entity.UserProfile;
import org.springframework.stereotype.Service;
import yan.goodshare.mapper.UserMapper;
import yan.goodshare.entity.User;
//
import yan.goodshare.user.profile.UserProfileUpdateRequest;

import java.util.Optional;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
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
        // In a real application, you would also calculate postCount, followerCount, and followingCount
        return new UserProfile(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getBio(),
                user.getAvatarUrl(),
                0, 0, 0
        );
    }

    public User updateUserProfile(User user, UserProfileUpdateRequest request) {
        user.setEmail(request.getEmail());
        user.setBio(request.getBio());
        user.setAvatarUrl(request.getAvatarUrl());
        userMapper.updateById(user);
        return user;
    }
}
