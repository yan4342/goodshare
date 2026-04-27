package yan.goodshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import yan.goodshare.entity.AppraisalComment;

import java.util.List;

@Mapper
public interface AppraisalCommentMapper extends BaseMapper<AppraisalComment> {

    @Select("SELECT c.*, u.username, u.nickname, u.avatar_url, u.level, u.active_style FROM appraisal_comments c JOIN users u ON c.user_id = u.id WHERE c.appraisal_id = #{appraisalId} ORDER BY c.created_at ASC")
        @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url"),
            @Result(property = "user.level", column = "level"),
            @Result(property = "user.activeStyle", column = "active_style")
        })
    List<AppraisalComment> selectCommentsWithUser(Long appraisalId);

    @org.apache.ibatis.annotations.Delete("DELETE FROM appraisal_comments WHERE appraisal_id = #{appraisalId}")
    void deleteByAppraisalId(Long appraisalId);
}
