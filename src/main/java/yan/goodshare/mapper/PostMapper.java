package yan.goodshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.*;
import yan.goodshare.entity.Post;
import yan.goodshare.entity.Tag;

import java.util.List;
import java.util.Set;

public interface PostMapper extends BaseMapper<Post> {

    @Select("SELECT p.*, (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) as like_count, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id WHERE p.status != 2 ORDER BY p.created_at DESC")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url"),
            @Result(property = "likeCount", column = "like_count")
    })
    List<Post> selectPostsWithUser();

    @Select("SELECT p.*, (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) as like_count, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id WHERE p.status != 2 ORDER BY p.created_at DESC")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url"),
            @Result(property = "likeCount", column = "like_count")
    })
    IPage<Post> selectPostsWithUserPage(IPage<Post> page);

    @Select("SELECT p.*, (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) as like_count, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id WHERE p.user_id = #{userId} AND p.status != 2 ORDER BY p.created_at DESC")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url"),
            @Result(property = "likeCount", column = "like_count")
    })
    List<Post> selectPostsByUserIdWithUser(Long userId);

    @Select("SELECT p.*, (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) as like_count, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id WHERE p.user_id = #{userId} ORDER BY p.created_at DESC")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url"),
            @Result(property = "likeCount", column = "like_count")
    })
    List<Post> selectPostsByUserIdWithUserIgnoreStatus(Long userId);

    @Select("SELECT p.*, (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) as like_count, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id WHERE p.id = #{id} AND p.status != 2")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url"),
            @Result(property = "likeCount", column = "like_count")
    })
    Post selectPostWithUserById(Long id);

    @Select("SELECT p.*, (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) as like_count, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id WHERE p.id = #{id}")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url"),
            @Result(property = "likeCount", column = "like_count")
    })
    Post selectPostWithUserByIdIgnoreStatus(Long id);

    @Select("SELECT p.*, (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) as like_count, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id JOIN post_tags pt ON p.id = pt.post_id JOIN tags t ON pt.tag_id = t.id WHERE t.name = #{tagName} AND p.status != 2 ORDER BY p.created_at DESC")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url"),
            @Result(property = "likeCount", column = "like_count")
    })
    List<Post> selectPostsByTagName(String tagName);

    @Select("SELECT p.*, (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) as like_count, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id JOIN post_tags pt ON p.id = pt.post_id JOIN tags t ON pt.tag_id = t.id WHERE t.name = #{tagName} AND p.status != 2 ORDER BY p.created_at DESC")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url"),
            @Result(property = "likeCount", column = "like_count")
    })
    IPage<Post> selectPostsByTagNamePage(IPage<Post> page, String tagName);

    @Select("SELECT p.*, (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) as like_count, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id WHERE p.status = 0 ORDER BY p.created_at DESC")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url"),
            @Result(property = "likeCount", column = "like_count")
    })
    IPage<Post> selectPendingPostsPage(IPage<Post> page);

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

    @Select("SELECT p.*, (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) as like_count, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id JOIN post_tags pt ON p.id = pt.post_id WHERE pt.tag_id = #{tagId} AND p.status != 2 ORDER BY p.created_at DESC LIMIT #{limit}")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url"),
            @Result(property = "likeCount", column = "like_count")
    })
    List<Post> selectPostsByTagId(@Param("tagId") Long tagId, @Param("limit") int limit);

    @Select("<script>" +
            "SELECT p.*, (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) as like_count, u.username, u.nickname, u.avatar_url " +
            "FROM posts p JOIN users u ON p.user_id = u.id " +
            "WHERE p.status != 2 AND p.id IN " +
            "<foreach item='item' index='index' collection='ids' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "</script>")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url"),
            @Result(property = "likeCount", column = "like_count")
    })
    List<Post> selectPostsWithUserByIds(@Param("ids") List<Long> ids);

    @Select("SELECT p.*, (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) as like_count, u.username, u.nickname, u.avatar_url " +
            "FROM posts p JOIN users u ON p.user_id = u.id " +
            "WHERE p.status != 2 " +
            "ORDER BY p.created_at DESC LIMIT #{limit}")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url"),
            @Result(property = "likeCount", column = "like_count")
    })
    List<Post> selectRecentPostsWithUser(int limit);

    @Select("<script>" +
            "SELECT p.*, (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) as like_count, u.username, u.nickname, u.avatar_url " +
            "FROM posts p JOIN users u ON p.user_id = u.id " +
            "WHERE p.status != 2 " +
            "<if test='excludedIds != null and !excludedIds.isEmpty()'>" +
            "AND p.id NOT IN " +
            "<foreach item='item' index='index' collection='excludedIds' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "</if>" +
            "ORDER BY p.view_count DESC, like_count DESC, p.created_at DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url"),
            @Result(property = "likeCount", column = "like_count")
    })
    List<Post> selectHotPostsWithUser(@Param("limit") int limit, @Param("offset") int offset, @Param("excludedIds") List<Long> excludedIds);
}
