package yan.goodshare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notifications")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recipientId;
    private Long senderId;
    private String type; // LIKE, COMMENT, FOLLOW
    private Long relatedId; // Post ID or other related entity ID
    private Boolean isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    // Auxiliary fields for display (not in DB table, populated via JOINs or separate queries)
    @TableField(exist = false)
    private User sender;
    
    @TableField(exist = false)
    private Post relatedPost;
}
