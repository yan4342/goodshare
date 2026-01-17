package yan.goodshare.user.profile;

import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    private String nickname;
    private String email;
    private String bio;
    private String avatarUrl;

    public UserProfileUpdateRequest(String nickname, String email, String bio, String avatarUrl) {
        this.nickname = nickname;
        this.email = email;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
    }

    public String getNickname() {
        return nickname;
    }
    public String getEmail() {
        return email;
    }
    public String getBio() {
        return bio;
    }
    public String getAvatarUrl() {
        return avatarUrl;
    }
}
