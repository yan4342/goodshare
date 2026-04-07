package yan.goodshare.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
//@AllArgsConstructor
public class UserProfile {
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String bio;
    private String avatarUrl;
    private int postCount;
    private int followerCount;
    private int followingCount;
    private Integer level;
    private Integer experience;
    private Integer activeStyle;

    // Manual All-Args Constructor
    public UserProfile(Long id, String username, String nickname, String email, String bio, String avatarUrl, int postCount, int followerCount, int followingCount, Integer level, Integer experience, Integer activeStyle) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.email = email;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
        this.postCount = postCount;
        this.followerCount = followerCount;
        this.followingCount = followingCount;
        this.level = level;
        this.experience = experience;
        this.activeStyle = activeStyle;
    }
}
