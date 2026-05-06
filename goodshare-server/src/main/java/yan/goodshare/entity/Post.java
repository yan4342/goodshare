package yan.goodshare.entity;

import com.baomidou.mybatisplus.annotation.*;

import jakarta.validation.constraints.NotEmpty;
import yan.goodshare.entity.Tag;
import yan.goodshare.entity.User;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@TableName("posts")
public class Post {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotEmpty
    private String title;

    private String content;

    private String coverUrl;

    private String images; // JSON array of image URLs

    @TableField(exist = false)
    private User user;

    @TableField("user_id")
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private Set<Tag> tags = new HashSet<>();

    @TableField("like_count")
    private Integer likeCount = 0;

    @TableField("comment_count")
    private Integer commentCount = 0;

    @TableField(exist = false)
    private Boolean isFollowedAuthor = false;

    @TableField(exist = false)
    private Boolean isLiked = false;

    @TableField(exist = false)
    private Boolean isFavorited = false;

    @TableField("view_count")
    private Integer viewCount = 0;

    @TableField(exist = false)
    private LocalDateTime viewTime;

    /**
     * Status: 0=Pending (Visible), 1=Approved (Visible), 2=Rejected (Hidden)
     */
    @TableField("status")
    private Integer status = 0;

    // Getters and Setters

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public Integer getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public Integer getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(Integer commentCount) {
        this.commentCount = commentCount;
    }

    public Boolean getIsFollowedAuthor() {
        return isFollowedAuthor;
    }

    public void setIsFollowedAuthor(Boolean isFollowedAuthor) {
        this.isFollowedAuthor = isFollowedAuthor;
    }

    public Boolean getIsLiked() {
        return isLiked;
    }

    public void setIsLiked(Boolean isLiked) {
        this.isLiked = isLiked;
    }

    public Boolean getIsFavorited() {
        return isFavorited;
    }

    public void setIsFavorited(Boolean isFavorited) {
        this.isFavorited = isFavorited;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getImages() {
        return images;
    }

    public void setImages(String images) {
        this.images = images;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getViewTime() {
        return viewTime;
    }

    public void setViewTime(LocalDateTime viewTime) {
        this.viewTime = viewTime;
    }
}
