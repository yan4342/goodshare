package yan.goodshare.user.profile;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private Long id;
    private String username;
    private String email;
    private String bio;
    private String avatarUrl;
    private int postCount;
    private int followerCount;
    private int followingCount;
}
