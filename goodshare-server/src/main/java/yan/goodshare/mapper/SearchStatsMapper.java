package yan.goodshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import yan.goodshare.entity.SearchStats;
import java.util.List;

@Mapper
public interface SearchStatsMapper extends BaseMapper<SearchStats> {

    @Update("INSERT INTO search_stats (keyword, search_count, created_at, updated_at) VALUES (#{keyword}, 1, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE search_count = search_count + 1, updated_at = NOW()")
    void incrementSearchCount(String keyword);

    @Select("SELECT * FROM search_stats ORDER BY search_count DESC LIMIT #{limit}")
    List<SearchStats> selectTopKeywords(int limit);
}
