package yan.goodshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import yan.goodshare.entity.Like;
import yan.goodshare.entity.Post;


public interface LikeMapper extends BaseMapper<Like> {
    @Select("SELECT p.*, u.username, u.avatar_url FROM posts p " +
            "JOIN post_likes l ON p.id = l.post_id " +
            "JOIN users u ON p.user_id = u.id " +
            "WHERE l.user_id = #{userId}")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.avatarUrl", column = "avatar_url")
    })
    IPage<Post> selectLikedPostsPage(IPage<Post> page, @Param("userId") Long userId);
}
