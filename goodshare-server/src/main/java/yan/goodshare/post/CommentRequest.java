package yan.goodshare.post;

import jakarta.validation.constraints.NotEmpty;

public class CommentRequest {

    @NotEmpty
    private String content;

    // Getters and Setters

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
