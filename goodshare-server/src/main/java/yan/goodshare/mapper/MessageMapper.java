package yan.goodshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import yan.goodshare.entity.Message;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
    

}
