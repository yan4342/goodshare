package yan.goodshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import yan.goodshare.entity.Comment;

import java.util.List;

public interface CommentMapper extends BaseMapper<Comment> {

    @Select("SELECT c.*, u.username, u.avatar_url FROM comments c JOIN users u ON c.user_id = u.id WHERE c.post_id = #{postId} ORDER BY c.created_at ASC")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.avatarUrl", column = "avatar_url")
    })
    List<Comment> selectCommentsWithUser(Long postId);
}
