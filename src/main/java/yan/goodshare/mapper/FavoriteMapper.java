package yan.goodshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import yan.goodshare.entity.Favorite;
import yan.goodshare.entity.Post;

import java.util.List;

public interface FavoriteMapper extends BaseMapper<Favorite> {
    @Select("SELECT p.*, u.username, u.avatar_url FROM posts p " +
            "JOIN favorites f ON p.id = f.post_id " +
            "JOIN users u ON p.user_id = u.id " +
            "WHERE f.user_id = #{userId}")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.avatarUrl", column = "avatar_url")
    })
    List<Post> selectFavoritedPosts(Long userId);
}
