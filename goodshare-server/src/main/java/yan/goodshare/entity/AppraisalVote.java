package yan.goodshare.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("appraisal_votes")
public class AppraisalVote {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("appraisal_id")
    private Long appraisalId;

    @TableField("user_id")
    private Long userId;

    @TableField("vote_type")
    private Integer voteType; // 1: Real, 2: Fake

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
