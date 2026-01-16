package yan.goodshare.post;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import yan.goodshare.mapper.CommentMapper;
import yan.goodshare.mapper.PostMapper;
import yan.goodshare.mapper.UserMapper;
import yan.goodshare.entity.Comment;
import yan.goodshare.entity.Post;
import yan.goodshare.entity.User;

import java.util.List;

@Service
public class CommentService {

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;

    public CommentService(CommentMapper commentMapper, PostMapper postMapper, UserMapper userMapper) {
        this.commentMapper = commentMapper;
        this.postMapper = postMapper;
        this.userMapper = userMapper;
    }

    public Comment createComment(Long postId, CommentRequest commentRequest) {
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

        Comment comment = new Comment();
        comment.setContent(commentRequest.getContent());
        comment.setPost_id(postId);
        comment.setUser_id(user.getId());

        commentMapper.insert(comment);
        return comment;
    }

    public List<Comment> getCommentsByPostId(Long postId) {
        return commentMapper.selectCommentsWithUser(postId);
    }
}
