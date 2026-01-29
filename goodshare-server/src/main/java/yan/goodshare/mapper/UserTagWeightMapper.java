package yan.goodshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import yan.goodshare.entity.UserTagWeight;

import java.util.List;

public interface UserTagWeightMapper extends BaseMapper<UserTagWeight> {
    @Select("SELECT utw.*, t.name as tag_name FROM user_tag_weights utw LEFT JOIN tags t ON utw.tag_id = t.id WHERE utw.user_id = #{userId}")
    List<UserTagWeight> selectByUserId(Long userId);
}
