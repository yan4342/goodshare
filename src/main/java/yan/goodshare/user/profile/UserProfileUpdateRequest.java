package yan.goodshare.user.profile;

import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    private String email;
    private String bio;
    private String avatarUrl;

    public UserProfileUpdateRequest(String email, String bio, String avatarUrl) {
        this.email = email;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
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
