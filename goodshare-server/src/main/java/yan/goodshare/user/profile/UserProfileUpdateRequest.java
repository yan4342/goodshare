package yan.goodshare.user.profile;

import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    private String nickname;
    private String email;
    private String bio;
    private String avatarUrl;
    private Integer activeStyle;

    public UserProfileUpdateRequest() {
    }

    public UserProfileUpdateRequest(String nickname, String email, String bio, String avatarUrl, Integer activeStyle) {
        this.nickname = nickname;
        this.email = email;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
        this.activeStyle = activeStyle;
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
    public Integer getActiveStyle() {
        return activeStyle;
    }
}
