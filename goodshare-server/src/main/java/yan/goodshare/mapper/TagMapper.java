package yan.goodshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import yan.goodshare.entity.Tag;

import java.util.List;
import java.util.Map;

public interface TagMapper extends BaseMapper<Tag> {
    @Delete("DELETE FROM post_tags WHERE tag_id = #{tagId}")
    void deletePostTagsByTagId(Long tagId);

    @Select("SELECT t.id, t.name, COUNT(pt.post_id) as post_count " +
            "FROM tags t " +
            "JOIN post_tags pt ON t.id = pt.tag_id " +
            "GROUP BY t.id, t.name " +
            "ORDER BY post_count DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> selectTrendingTags(@Param("limit") int limit);
}
