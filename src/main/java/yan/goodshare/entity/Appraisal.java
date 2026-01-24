package yan.goodshare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("appraisals")
public class Appraisal {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("product_name")
    private String productName;

    private String description;

    private String images; // JSON array of URLs

    private Integer status; // 0: Normal, 1: Hidden

    @TableField("real_votes")
    private Integer realVotes;

    @TableField("fake_votes")
    private Integer fakeVotes;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private User user;

    @TableField(exist = false)
    private Integer currentUserVote; // 1: Real, 2: Fake, 0: None
}
