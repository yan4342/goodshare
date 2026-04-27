package yan.goodshare.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDateTime;

@TableName("appraisal_comments")
public class AppraisalComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotEmpty
    private String content;

    private Long appraisalId;

    private Long userId;

    private Long parentId;

    @TableField(exist = false)
    private User user;

    @TableField(exist = false)
    private Long likeCount;

    @TableField(exist = false)
    private Boolean isLiked;

    @TableField(exist = false)
    private java.util.List<AppraisalComment> replies = new java.util.ArrayList<>();

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getAppraisalId() {
        return appraisalId;
    }

    public void setAppraisalId(Long appraisalId) {
        this.appraisalId = appraisalId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Long likeCount) {
        this.likeCount = likeCount;
    }

    public Boolean getIsLiked() {
        return isLiked;
    }

    public void setIsLiked(Boolean isLiked) {
        this.isLiked = isLiked;
    }

    public java.util.List<AppraisalComment> getReplies() {
        return replies;
    }

    public void setReplies(java.util.List<AppraisalComment> replies) {
        this.replies = replies;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
