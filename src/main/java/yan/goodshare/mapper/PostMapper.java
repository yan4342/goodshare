package yan.goodshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import yan.goodshare.entity.Post;
import yan.goodshare.entity.Tag;

import java.util.List;
import java.util.Set;

public interface PostMapper extends BaseMapper<Post> {

    @Select("SELECT p.*, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id ORDER BY p.created_at DESC")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url")
    })
    List<Post> selectPostsWithUser();

    @Select("SELECT p.*, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id WHERE p.user_id = #{userId} ORDER BY p.created_at DESC")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url")
    })
    List<Post> selectPostsByUserIdWithUser(Long userId);

    @Select("SELECT p.*, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id WHERE p.id = #{id}")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url")
    })
    Post selectPostWithUserById(Long id);

    @Select("SELECT p.*, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id JOIN post_tags pt ON p.id = pt.post_id JOIN tags t ON pt.tag_id = t.id WHERE t.name = #{tagName} ORDER BY p.created_at DESC")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url")
    })
    List<Post> selectPostsByTagName(String tagName);

    @Insert("INSERT INTO post_tags (post_id, tag_id) VALUES (#{postId}, #{tagId})")
    void insertPostTag(@Param("postId") Long postId, @Param("tagId") Long tagId);

    @Select("SELECT t.* FROM tags t JOIN post_tags pt ON t.id = pt.tag_id WHERE pt.post_id = #{postId}")
    Set<Tag> selectTagsByPostId(Long postId);

    @Delete("DELETE FROM post_tags WHERE post_id = #{postId}")
    void deletePostTags(Long postId);

    @Delete("DELETE FROM comments WHERE post_id = #{postId}")
    void deletePostComments(Long postId);

    @Delete("DELETE FROM post_likes WHERE post_id = #{postId}")
    void deletePostLikes(Long postId);

    @Delete("DELETE FROM favorites WHERE post_id = #{postId}")
    void deletePostFavorites(Long postId);
}
