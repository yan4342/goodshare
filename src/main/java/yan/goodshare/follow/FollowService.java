package yan.goodshare.follow;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import yan.goodshare.user.User;
import yan.goodshare.user.UserRepository;

import java.util.List;
import java.util.Optional;

@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    public FollowService(FollowRepository followRepository, UserRepository userRepository) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
    }

    public void followUser(Long followedId) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();

        User follower = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        User followed = userRepository.findById(followedId)
                .orElseThrow(() -> new RuntimeException("User to follow not found"));

        if (follower.getId().equals(followed.getId())) {
            throw new RuntimeException("You cannot follow yourself");
        }

        Optional<Follow> existingFollow = followRepository.findByFollowerIdAndFollowedId(follower.getId(), followedId);
        if (existingFollow.isPresent()) {
            throw new RuntimeException("You are already following this user");
        }

        Follow follow = new Follow();
        follow.setFollower(follower);
        follow.setFollowed(followed);

        followRepository.save(follow);
    }

    public void unfollowUser(Long followedId) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();

        User follower = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Follow follow = followRepository.findByFollowerIdAndFollowedId(follower.getId(), followedId)
                .orElseThrow(() -> new RuntimeException("You are not following this user"));

        followRepository.delete(follow);
    }

    public List<Follow> getFollowing() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return followRepository.findByFollowerId(user.getId());
    }

    public List<Follow> getFollowers() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return followRepository.findByFollowedId(user.getId());
    }

    public long getFollowingCount(Long userId) {
        return followRepository.countByFollowerId(userId);
    }

    public long getFollowersCount(Long userId) {
        return followRepository.countByFollowedId(userId);
    }
}
