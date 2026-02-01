package yan.goodshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import yan.goodshare.entity.CommentLike;

public interface CommentLikeMapper extends BaseMapper<CommentLike> {
    @Select("SELECT COUNT(*) FROM comment_likes WHERE comment_id = #{commentId}")
    Long countByCommentId(Long commentId);

    @Select("SELECT COUNT(*) > 0 FROM comment_likes WHERE comment_id = #{commentId} AND user_id = #{userId}")
    boolean existsByCommentIdAndUserId(Long commentId, Long userId);
}
