package yan.goodshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import yan.goodshare.entity.Message;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
    
    // Custom query to find latest message for each conversation involving the user
    // This is a bit complex in SQL, might be easier to do in service or simple SQL
    // A conversation is defined by a pair of (sender, receiver) regardless of order.
    
    // We can fetch all messages involving the user and process in memory if volume is low, 
    // or use a smart GROUP BY.
    // For now, let's keep it simple and maybe handle in Service or add a custom XML mapper if needed.
    // But since we want to avoid XML if possible, let's try annotation or query wrapper.
}
