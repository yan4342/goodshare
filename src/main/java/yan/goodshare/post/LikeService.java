package yan.goodshare.post;

import yan.goodshare.entity.Like;
import yan.goodshare.entity.Post;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import yan.goodshare.mapper.LikeMapper;
import yan.goodshare.mapper.PostMapper;
import yan.goodshare.mapper.UserMapper;
import yan.goodshare.entity.User;

@Service
public class LikeService {

    private final LikeMapper likeMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;

    public LikeService(LikeMapper likeMapper, PostMapper postMapper, UserMapper userMapper) {
        this.likeMapper = likeMapper;
        this.postMapper = postMapper;
        this.userMapper = userMapper;
    }

    public void likePost(Long postId) {
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

        Like existingLike = likeMapper.selectOne(new QueryWrapper<Like>()
                .eq("user_id", user.getId())
                .eq("post_id", postId));
        if (existingLike != null) {
            throw new RuntimeException("You have already liked this post");
        }

        Like like = new Like();
        like.setUser_id(user.getId());
        like.setPost_id(postId);

        likeMapper.insert(like);
    }

    public void unlikePost(Long postId) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();

        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        int deleted = likeMapper.delete(new QueryWrapper<Like>()
                .eq("user_id", user.getId())
                .eq("post_id", postId));

        if (deleted == 0) {
            throw new RuntimeException("You have not liked this post");
        }
    }

    public long getLikeCount(Long postId) {
        return likeMapper.selectCount(new QueryWrapper<Like>().eq("post_id", postId));
    }
}
