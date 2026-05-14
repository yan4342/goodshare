package yan.goodshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.*;
import yan.goodshare.entity.Post;
import yan.goodshare.entity.Tag;

import java.util.List;
import java.util.Set;

public interface PostMapper extends BaseMapper<Post> {

    @Select("SELECT p.*, u.username, u.nickname, u.avatar_url, u.level, u.active_style FROM posts p JOIN users u ON p.user_id = u.id WHERE (p.status != 2 OR p.status IS NULL) ORDER BY p.created_at DESC")
    @Results(id = "postWithUserResult", value = {
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url"),
            @Result(property = "user.level", column = "level"),
            @Result(property = "user.activeStyle", column = "active_style")
    })
    List<Post> selectPostsWithUser();

    @Select("SELECT p.*, u.username, u.nickname, u.avatar_url, u.level, u.active_style FROM posts p JOIN users u ON p.user_id = u.id ORDER BY p.created_at DESC")
    @ResultMap("postWithUserResult")
    List<Post> selectAllPostsWithUser();

    @Select("SELECT p.*, u.username, u.nickname, u.avatar_url, u.level, u.active_style FROM posts p JOIN users u ON p.user_id = u.id WHERE (p.status != 2 OR p.status IS NULL) ORDER BY p.created_at DESC")
    @ResultMap("postWithUserResult")
    IPage<Post> selectPostsWithUserPage(IPage<Post> page);

    @Select("<script>" +
            "SELECT p.*, u.username, u.nickname, u.avatar_url, u.level, u.active_style " +
            "FROM posts p JOIN users u ON p.user_id = u.id " +
            "WHERE (p.status != 2 OR p.status IS NULL) " +
            "AND (p.title LIKE CONCAT('%', #{keyword}, '%') OR p.content LIKE CONCAT('%', #{keyword}, '%') OR u.username LIKE CONCAT('%', #{keyword}, '%') OR u.nickname LIKE CONCAT('%', #{keyword}, '%')) " +
            "ORDER BY p.created_at DESC" +
            "</script>")
    @ResultMap("postWithUserResult")
    IPage<Post> selectAdminPostsByKeyword(IPage<Post> page, @Param("keyword") String keyword);

    @Select("SELECT p.*, u.username, u.nickname, u.avatar_url, u.level, u.active_style FROM posts p JOIN users u ON p.user_id = u.id WHERE p.user_id = #{userId} AND (p.status != 2 OR p.status IS NULL) ORDER BY p.created_at DESC")
    @ResultMap("postWithUserResult")
    IPage<Post> selectPostsByUserIdWithUser(IPage<Post> page, @Param("userId") Long userId);

    @Select("SELECT p.*, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id WHERE p.user_id = #{userId} ORDER BY p.created_at DESC")
    @Results(id = "postWithUserBasicResult", value = {
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url")
    })
    IPage<Post> selectPostsByUserIdWithUserIgnoreStatus(IPage<Post> page, @Param("userId") Long userId);

    @Select("SELECT p.*, u.username, u.nickname, u.avatar_url, u.level, u.active_style FROM posts p JOIN users u ON p.user_id = u.id WHERE p.id = #{id} AND (p.status != 2 OR p.status IS NULL)")
    @ResultMap("postWithUserResult")
    Post selectPostWithUserById(Long id);

    @Select("SELECT p.*, u.username, u.nickname, u.avatar_url, u.level, u.active_style FROM posts p JOIN users u ON p.user_id = u.id WHERE p.id = #{id}")
    @ResultMap("postWithUserResult")
    Post selectPostWithUserByIdIgnoreStatus(Long id);

    @Select("SELECT p.*, u.username, u.nickname, u.avatar_url, u.level, u.active_style FROM posts p JOIN users u ON p.user_id = u.id JOIN post_tags pt ON p.id = pt.post_id JOIN tags t ON pt.tag_id = t.id WHERE t.name = #{tagName} AND (p.status != 2 OR p.status IS NULL) ORDER BY p.created_at DESC")
    @ResultMap("postWithUserResult")
    List<Post> selectPostsByTagName(String tagName);

    @Select("SELECT p.*, " +
            "(SELECT COUNT(*) > 0 FROM post_likes pl WHERE pl.post_id = p.id AND pl.user_id = #{userId}) as is_liked, " +
            "(SELECT COUNT(*) > 0 FROM favorites fav WHERE fav.post_id = p.id AND fav.user_id = #{userId}) as is_favorited, " +
            "u.username, u.nickname, u.avatar_url " +
            "FROM posts p " +
            "JOIN users u ON p.user_id = u.id " +
            "JOIN follows f ON p.user_id = f.followed_id " +
            "WHERE f.follower_id = #{userId} AND (p.status != 2 OR p.status IS NULL) " +
            "ORDER BY p.created_at DESC")
    @Results(id = "postWithFollowResult", value = {
            @Result(property = "userId", column = "user_id"),
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url"),
            @Result(property = "isLiked", column = "is_liked"),
            @Result(property = "isFavorited", column = "is_favorited")
    })
    IPage<Post> selectFollowedPostsPage(IPage<Post> page, @Param("userId") Long userId);

    @Select("<script>" +
            "SELECT p.*, u.username, u.nickname, u.avatar_url " +
            "FROM posts p " +
            "JOIN users u ON p.user_id = u.id " +
            "JOIN follows f ON p.user_id = f.followed_id " +
            "WHERE f.follower_id = #{userId} AND (p.status != 2 OR p.status IS NULL) " +
            "<if test='excludedPostIds != null and !excludedPostIds.isEmpty()'>" +
            "AND p.id NOT IN " +
            "<foreach item='item' index='index' collection='excludedPostIds' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "</if>" +
            "ORDER BY p.created_at DESC LIMIT #{limit}" +
            "</script>")
    @ResultMap("postWithUserBasicResult")
    List<Post> selectRecentFollowedPosts(@Param("userId") Long userId, @Param("limit") int limit, @Param("excludedPostIds") List<Long> excludedPostIds);

    @Select("SELECT p.*, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id JOIN post_tags pt ON p.id = pt.post_id JOIN tags t ON pt.tag_id = t.id WHERE t.name = #{tagName} AND (p.status != 2 OR p.status IS NULL) ORDER BY p.created_at DESC")
    @ResultMap("postWithUserBasicResult")
    IPage<Post> selectPostsByTagNamePage(IPage<Post> page, String tagName);

    @Select("SELECT p.*, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id WHERE p.status = 0 ORDER BY p.created_at DESC")
    @ResultMap("postWithUserBasicResult")
    IPage<Post> selectPendingPostsPage(IPage<Post> page);

    @Insert("INSERT INTO post_tags (post_id, tag_id) VALUES (#{postId}, #{tagId})")
    void insertPostTag(@Param("postId") Long postId, @Param("tagId") Long tagId);

    @Select("SELECT t.* FROM tags t JOIN post_tags pt ON t.id = pt.tag_id WHERE pt.post_id = #{postId}")
    Set<Tag> selectTagsByPostId(Long postId);

    @Select("SELECT pt.post_id FROM post_tags pt WHERE pt.tag_id = #{tagId} AND (SELECT COUNT(DISTINCT pt2.tag_id) FROM post_tags pt2 WHERE pt2.post_id = pt.post_id) = 1")
    List<Long> selectPostIdsWithOnlyTag(@Param("tagId") Long tagId);

    @Select("SELECT pt.post_id FROM post_tags pt WHERE pt.tag_id = #{tagId} AND (SELECT COUNT(DISTINCT pt2.tag_id) FROM post_tags pt2 WHERE pt2.post_id = pt.post_id) > 1")
    List<Long> selectPostIdsWithMultipleTags(@Param("tagId") Long tagId);

    @Delete("<script>" +
            "DELETE FROM post_tags WHERE tag_id = #{tagId} AND post_id IN " +
            "<foreach item='item' index='index' collection='postIds' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "</script>")
    void deleteTagFromPosts(@Param("tagId") Long tagId, @Param("postIds") List<Long> postIds);

    @Select("<script>" +
            "SELECT pt.post_id, t.* FROM tags t " +
            "JOIN post_tags pt ON t.id = pt.tag_id " +
            "WHERE pt.post_id IN " +
            "<foreach item='item' index='index' collection='postIds' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "</script>")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "name", column = "name")
    })
    List<java.util.Map<String, Object>> selectTagsByPostIds(@Param("postIds") List<Long> postIds);

    @Select("<script>" +
            "SELECT id as post_id, p.comment_count " +
            "FROM posts p " +
            "WHERE p.id IN " +
            "<foreach item='item' index='index' collection='postIds' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "</script>")
    List<java.util.Map<String, Object>> selectCommentCountsByPostIds(@Param("postIds") List<Long> postIds);

    @Select("<script>" +
            "SELECT id as post_id, p.view_count " +
            "FROM posts p " +
            "WHERE p.id IN " +
            "<foreach item='item' index='index' collection='postIds' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "</script>")
    List<java.util.Map<String, Object>> selectViewCountsByPostIds(@Param("postIds") List<Long> postIds);

    @Delete("DELETE FROM post_tags WHERE post_id = #{postId}")
    void deletePostTags(Long postId);

    @Delete("DELETE FROM comments WHERE post_id = #{postId}")
    void deletePostComments(Long postId);

    @Delete("DELETE FROM post_likes WHERE post_id = #{postId}")
    void deletePostLikes(Long postId);

    @Delete("DELETE FROM favorites WHERE post_id = #{postId}")
    void deletePostFavorites(Long postId);

    @Update("UPDATE posts SET like_count = GREATEST(like_count + #{delta}, 0) WHERE id = #{postId}")
    void updateLikeCount(@Param("postId") Long postId, @Param("delta") int delta);

    @Update("UPDATE posts SET comment_count = GREATEST(comment_count + #{delta}, 0) WHERE id = #{postId}")
    void updateCommentCount(@Param("postId") Long postId, @Param("delta") int delta);

    @Update("<script>" +
            "UPDATE posts SET like_count = GREATEST(like_count - (SELECT COUNT(*) FROM post_likes pl WHERE pl.user_id = #{userId} AND pl.post_id = posts.id), 0) " +
            "WHERE id IN (SELECT pl2.post_id FROM post_likes pl2 WHERE pl2.user_id = #{userId})" +
            "</script>")
    void decrementLikeCountByUser(@Param("userId") Long userId);

    @Update("<script>" +
            "UPDATE posts SET comment_count = GREATEST(comment_count - (SELECT COUNT(*) FROM comments c WHERE c.user_id = #{userId} AND c.post_id = posts.id), 0) " +
            "WHERE id IN (SELECT c2.post_id FROM comments c2 WHERE c2.user_id = #{userId})" +
            "</script>")
    void decrementCommentCountByUser(@Param("userId") Long userId);

    @Select("SELECT p.*, u.username, u.nickname, u.avatar_url FROM posts p JOIN users u ON p.user_id = u.id JOIN post_tags pt ON p.id = pt.post_id WHERE pt.tag_id = #{tagId} AND p.status != 2 ORDER BY p.created_at DESC LIMIT #{limit}")
    @ResultMap("postWithUserBasicResult")
    List<Post> selectPostsByTagId(@Param("tagId") Long tagId, @Param("limit") int limit);

    @Select("<script>" +
            "SELECT p.*, u.username, u.nickname, u.avatar_url " +
            "FROM posts p JOIN users u ON p.user_id = u.id " +
            "WHERE p.status != 2 AND p.id IN " +
            "<foreach item='item' index='index' collection='ids' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "</script>")
    @ResultMap("postWithUserBasicResult")
    List<Post> selectPostsWithUserByIds(@Param("ids") List<Long> ids);

    @Select("SELECT p.*, u.username, u.nickname, u.avatar_url " +
            "FROM posts p JOIN users u ON p.user_id = u.id " +
            "WHERE p.status != 2 " +
            "ORDER BY p.created_at DESC LIMIT #{limit}")
    @ResultMap("postWithUserBasicResult")
    List<Post> selectRecentPostsWithUser(int limit);

    @Select("<script>" +
            "SELECT p.*, u.username, u.nickname, u.avatar_url " +
            "FROM posts p JOIN users u ON p.user_id = u.id " +
            "WHERE p.status != 2 " +
            "<if test='excludedIds != null and !excludedIds.isEmpty()'>" +
            "AND p.id NOT IN " +
            "<foreach item='item' index='index' collection='excludedIds' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "</if>" +
            "ORDER BY p.view_count DESC, p.like_count DESC, p.created_at DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    @ResultMap("postWithUserBasicResult")
    List<Post> selectHotPostsWithUser(@Param("limit") int limit, @Param("offset") int offset, @Param("excludedIds") List<Long> excludedIds);

    @Select("SELECT p.*, " +
            "u.username, u.nickname, u.avatar_url, " +
            "pv.created_at as view_time " +
            "FROM posts p " +
            "JOIN post_views pv ON p.id = pv.post_id " +
            "JOIN users u ON p.user_id = u.id " +
            "WHERE pv.user_id = #{userId} AND (p.status != 2 OR p.status IS NULL) " +
            "ORDER BY pv.created_at DESC")
    @Results({
            @Result(property = "user.username", column = "username"),
            @Result(property = "user.nickname", column = "nickname"),
            @Result(property = "user.avatarUrl", column = "avatar_url"),
            @Result(property = "viewTime", column = "view_time")
    })
    IPage<Post> selectHistoryPostsPage(IPage<Post> page, @Param("userId") Long userId);
}
