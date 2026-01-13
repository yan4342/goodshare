package yan.goodshare.user.profile;

import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    private String email;
    private String bio;
    private String avatarUrl;
}
