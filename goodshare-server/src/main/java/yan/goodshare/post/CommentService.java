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

import java.time.LocalDateTime;
import java.util.List;

import yan.goodshare.service.NotificationService;
import yan.goodshare.search.SearchService;
import yan.goodshare.service.UserTagWeightService;

@Service
public class CommentService {

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final yan.goodshare.mapper.CommentLikeMapper commentLikeMapper;
    private final SearchService searchService;
    private final UserTagWeightService userTagWeightService;

    public CommentService(CommentMapper commentMapper, PostMapper postMapper, UserMapper userMapper, NotificationService notificationService, yan.goodshare.mapper.CommentLikeMapper commentLikeMapper, SearchService searchService, UserTagWeightService userTagWeightService) {
        this.commentMapper = commentMapper;
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
        this.commentLikeMapper = commentLikeMapper;
        this.searchService = searchService;
        this.userTagWeightService = userTagWeightService;
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
        comment.setPostId(postId);
        comment.setUserId(user.getId());
        comment.setParentId(commentRequest.getParentId()); // Handle reply
        comment.setCreatedAt(LocalDateTime.now());

        commentMapper.insert(comment);

        userTagWeightService.applyInteractionWeight(user.getId(), postId, "weight.comment");
        
        // Create notification
        // If it's a reply, notify the comment author? 
        // For now, existing logic notifies post author.
        // We should also notify parent comment author if it exists and is different.
        if (commentRequest.getParentId() != null) {
             Comment parent = commentMapper.selectById(commentRequest.getParentId());
             if (parent != null && !parent.getUserId().equals(user.getId())) {
                 notificationService.createNotification(parent.getUserId(), user.getId(), "REPLY", postId);
             }
        }
        
        // Always notify post author (unless it's self, logic inside notificationService handles it usually, or we check here)
        if (!post.getUserId().equals(user.getId())) {
             notificationService.createNotification(post.getUserId(), user.getId(), "COMMENT", postId);
        }

        // Update Search Index
        try {
            Post fullPost = postMapper.selectPostWithUserByIdIgnoreStatus(postId);
            if (fullPost != null) {
                searchService.indexPost(fullPost);
            }
        } catch (Exception e) {
            System.err.println("Failed to update search index for post " + postId + ": " + e.getMessage());
        }
        
        return comment;
    }

    public List<Comment> getCommentsByPostId(Long postId, String sort) {
        List<Comment> allComments = commentMapper.selectCommentsWithUser(postId);
        
        // Populate like info
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long currentUserId = null;
        if (principal instanceof UserDetails) {
            String username = ((UserDetails) principal).getUsername();
            User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
            if (user != null) {
                currentUserId = user.getId();
            }
        }

        for (Comment comment : allComments) {
            comment.setLikeCount(commentLikeMapper.countByCommentId(comment.getId()));
            if (currentUserId != null) {
                comment.setIsLiked(commentLikeMapper.existsByCommentIdAndUserId(comment.getId(), currentUserId));
            } else {
                comment.setIsLiked(false);
            }
        }

        // Build hierarchy
        List<Comment> rootComments = new java.util.ArrayList<>();
        java.util.Map<Long, Comment> commentMap = new java.util.HashMap<>();
        
        // First pass: Map all comments
        for (Comment c : allComments) {
            commentMap.put(c.getId(), c);
        }
        
        // Second pass: Assign children to parents
        for (Comment c : allComments) {
            if (c.getParentId() != null) {
                // Find root
                Comment root = findRoot(c, commentMap);
                if (root != null) {
                    // Only add to root's replies list
                    // If c is a direct child of root, it goes to root.replies
                    // If c is a child of a child (e.g., A -> B -> C), C goes to A.replies
                    // This flattens the display as requested: "b, c, d... attached to comment a"
                    if (root.getReplies() == null) root.setReplies(new java.util.ArrayList<>());
                    root.getReplies().add(c);
                } else {
                    rootComments.add(c);
                }
            } else {
                rootComments.add(c);
            }
        }

        // Sorting Root Comments
        if ("hot".equalsIgnoreCase(sort)) {
            rootComments.sort((c1, c2) -> {
                int likeCompare = c2.getLikeCount().compareTo(c1.getLikeCount());
                if (likeCompare != 0) return likeCompare;
                return c2.getCreatedAt().compareTo(c1.getCreatedAt()); // Tie-break with time (newest first)
            });
        } else if ("desc".equalsIgnoreCase(sort)) {
            rootComments.sort((c1, c2) -> c2.getCreatedAt().compareTo(c1.getCreatedAt()));
        } else {
            // Default ASC (Oldest first)
            rootComments.sort((c1, c2) -> c1.getCreatedAt().compareTo(c2.getCreatedAt()));
        }
        
        // Sort Replies (Always Oldest First to keep conversation flow)
        for (Comment root : rootComments) {
            if (root.getReplies() != null && !root.getReplies().isEmpty()) {
                root.getReplies().sort((c1, c2) -> c1.getCreatedAt().compareTo(c2.getCreatedAt()));
            }
        }

        return rootComments;
    }

    private Comment findRoot(Comment c, java.util.Map<Long, Comment> map) {
        Comment current = c;
        // Safety counter to prevent infinite loops in case of circular references (though unlikely in DB)
        int depth = 0;
        while (current.getParentId() != null && depth < 100) {
            Comment parent = map.get(current.getParentId());
            if (parent == null) return null; // Parent missing
            current = parent;
            depth++;
        }
        return current;
    }

    public void likeComment(Long commentId) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", userDetails.getUsername()));
        
        yan.goodshare.entity.CommentLike like = new yan.goodshare.entity.CommentLike();
        like.setCommentId(commentId);
        like.setUserId(user.getId());
        try {
            commentLikeMapper.insert(like);
        } catch (Exception e) {
            // Ignore duplicate key
        }
    }

    public void unlikeComment(Long commentId) {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", userDetails.getUsername()));
        
        commentLikeMapper.delete(new QueryWrapper<yan.goodshare.entity.CommentLike>()
            .eq("comment_id", commentId)
            .eq("user_id", user.getId()));
    }
}
