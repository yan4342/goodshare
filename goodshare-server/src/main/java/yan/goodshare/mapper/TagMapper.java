package yan.goodshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import yan.goodshare.entity.Tag;

public interface TagMapper extends BaseMapper<Tag> {
    @Delete("DELETE FROM post_tags WHERE tag_id = #{tagId}")
    void deletePostTagsByTagId(Long tagId);
}
