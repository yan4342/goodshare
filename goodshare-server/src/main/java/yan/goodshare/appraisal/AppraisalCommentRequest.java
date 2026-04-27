package yan.goodshare.appraisal;

import jakarta.validation.constraints.NotEmpty;

public class AppraisalCommentRequest {

    @NotEmpty
    private String content;

    private Long parentId;

    // Getters and Setters

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }
}
