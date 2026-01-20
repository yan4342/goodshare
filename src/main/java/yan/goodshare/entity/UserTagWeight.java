package yan.goodshare.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_tag_weights")
public class UserTagWeight {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long tagId;
    private Double weight;

    @TableField(exist = false)
    private String tagName;
}
