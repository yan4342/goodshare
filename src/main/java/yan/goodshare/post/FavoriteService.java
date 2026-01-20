package yan.goodshare.post;

import yan.goodshare.entity.Favorite;
import yan.goodshare.entity.Post;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import yan.goodshare.mapper.FavoriteMapper;
import yan.goodshare.mapper.PostMapper;
import yan.goodshare.mapper.UserMapper;
import yan.goodshare.entity.User;

import java.util.List;

@Service
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;

    public FavoriteService(FavoriteMapper favoriteMapper, PostMapper postMapper, UserMapper userMapper) {
        this.favoriteMapper = favoriteMapper;
        this.postMapper = postMapper;
        this.userMapper = userMapper;
    }

    public void favoritePost(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new RuntimeException("Post not found");
        }

        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();

        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        Favorite existingFavorite = favoriteMapper.selectOne(new QueryWrapper<Favorite>()
                .eq("user_id", user.getId())
                .eq("post_id", postId));
        if (existingFavorite != null) {
            throw new RuntimeException("You have already favorited this post");
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(user.getId());
        favorite.setPostId(postId);

        favoriteMapper.insert(favorite);
    }

    public void unfavoritePost(Long postId) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();

        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        int deleted = favoriteMapper.delete(new QueryWrapper<Favorite>()
                .eq("user_id", user.getId())
                .eq("post_id", postId));

        if (deleted == 0) {
            throw new RuntimeException("You have not favorited this post");
        }
    }

    public List<Post> getUserFavorites() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();

        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        return favoriteMapper.selectFavoritedPosts(user.getId());
    }

    public boolean isFavorited(Long postId) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();

        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            return false;
        }

        Favorite favorite = favoriteMapper.selectOne(new QueryWrapper<Favorite>()
                .eq("user_id", user.getId())
                .eq("post_id", postId));
        return favorite != null;
    }
}
