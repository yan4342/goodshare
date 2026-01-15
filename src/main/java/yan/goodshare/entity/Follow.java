package yan.goodshare.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("follows")
public class Follow {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long followerId;

    private Long followedId;
}
