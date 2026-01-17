package yan.goodshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import yan.goodshare.entity.Notification;

import java.util.List;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
    
    @Select("SELECT n.*, " +
            "u.id as sender_id, u.username as sender_username, u.nickname as sender_nickname, u.avatar_url as sender_avatar_url, " +
            "p.id as post_id, p.title as post_title, p.images as post_images " +
            "FROM notifications n " +
            "LEFT JOIN users u ON n.sender_id = u.id " +
            "LEFT JOIN posts p ON n.related_id = p.id AND (n.type = 'LIKE' OR n.type = 'COMMENT') " +
            "WHERE n.recipient_id = #{userId} " +
            "ORDER BY n.created_at DESC")
    @Results({
            @Result(property = "sender.id", column = "sender_id"),
            @Result(property = "sender.username", column = "sender_username"),
            @Result(property = "sender.nickname", column = "sender_nickname"),
            @Result(property = "sender.avatarUrl", column = "sender_avatar_url"),
            @Result(property = "relatedPost.id", column = "post_id"),
            @Result(property = "relatedPost.title", column = "post_title"),
            @Result(property = "relatedPost.images", column = "post_images")
    })
    List<Notification> selectNotificationsWithDetails(@Param("userId") Long userId);
}
