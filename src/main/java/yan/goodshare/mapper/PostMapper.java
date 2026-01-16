package yan.goodshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Many;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import yan.goodshare.entity.Post;

import java.util.List;

public interface PostMapper extends BaseMapper<Post> {

    @Select("SELECT p.*, u.username, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.avatarUrl", column = "avatar_url")
    })
    List<Post> selectPostsWithUser();
}
