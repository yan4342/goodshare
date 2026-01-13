package yan.goodshare.post;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public class PostRequest {

    @NotEmpty
    private String title;

    @NotEmpty
    private String content;

    private Set<String> tags;

    // Getters and Setters

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
