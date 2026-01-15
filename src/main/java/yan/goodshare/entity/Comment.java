package yan.goodshare.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotEmpty;
import yan.goodshare.entity.Post;
import yan.goodshare.entity.User;

import java.time.LocalDateTime;

@TableName("comments")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotEmpty
    private String content;

    private Long post_id;

    private Long user_id;

    @TableField(exist = false)
    private Post post;

    @TableField(exist = false)
    private User user;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // Getters and Setters

    public Long getPost_id() {
        return post_id;
    }

    public void setPost_id(Long post_id) {
        this.post_id = post_id;
    }

    public Long getUser_id() {
        return user_id;
    }

    public void setUser_id(Long user_id) {
        this.user_id = user_id;
    }

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

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
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
}
