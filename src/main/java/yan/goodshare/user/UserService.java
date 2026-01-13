package yan.goodshare.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import yan.goodshare.user.profile.UserProfile;
import yan.goodshare.user.profile.UserProfileUpdateRequest;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User register(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
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
        return userRepository.save(user);
    }
}
