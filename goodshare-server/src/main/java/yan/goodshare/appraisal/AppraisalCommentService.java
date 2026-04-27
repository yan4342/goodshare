package yan.goodshare.appraisal;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yan.goodshare.entity.AppraisalComment;
import yan.goodshare.entity.Appraisal;
import yan.goodshare.entity.User;
import yan.goodshare.mapper.AppraisalCommentMapper;
import yan.goodshare.mapper.AppraisalMapper;
import yan.goodshare.mapper.UserMapper;
import yan.goodshare.service.NotificationService;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppraisalCommentService {

    private final AppraisalCommentMapper appraisalCommentMapper;
    private final AppraisalMapper appraisalMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    public AppraisalCommentService(AppraisalCommentMapper appraisalCommentMapper, AppraisalMapper appraisalMapper, UserMapper userMapper, NotificationService notificationService) {
        this.appraisalCommentMapper = appraisalCommentMapper;
        this.appraisalMapper = appraisalMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    @Transactional
    public AppraisalComment createComment(Long appraisalId, AppraisalCommentRequest commentRequest) {
        Appraisal appraisal = appraisalMapper.selectById(appraisalId);
        if (appraisal == null) {
            throw new RuntimeException("Appraisal not found");
        }

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserDetails)) {
            throw new RuntimeException("Unauthenticated");
        }
        String username = ((UserDetails) principal).getUsername();
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) throw new RuntimeException("User not found");

        AppraisalComment comment = new AppraisalComment();
        comment.setContent(commentRequest.getContent());
        comment.setAppraisalId(appraisalId);
        comment.setUserId(user.getId());
        comment.setParentId(commentRequest.getParentId());
        comment.setCreatedAt(LocalDateTime.now());

        appraisalCommentMapper.insert(comment);

        // Notify parent comment author if it's a reply
        if (commentRequest.getParentId() != null) {
            AppraisalComment parent = appraisalCommentMapper.selectById(commentRequest.getParentId());
            if (parent != null && !parent.getUserId().equals(user.getId())) {
                notificationService.createNotification(parent.getUserId(), user.getId(), "REPLY", appraisalId);
            }
        }

        // Notify appraisal author
        if (!appraisal.getUserId().equals(user.getId())) {
            notificationService.createNotification(appraisal.getUserId(), user.getId(), "COMMENT", appraisalId);
        }

        return comment;
    }

    public List<AppraisalComment> getCommentsByAppraisalId(Long appraisalId, String sort) {
        List<AppraisalComment> allComments = appraisalCommentMapper.selectCommentsWithUser(appraisalId);

        java.util.Map<Long, AppraisalComment> map = new java.util.HashMap<>();
        for (AppraisalComment c : allComments) map.put(c.getId(), c);

        List<AppraisalComment> roots = new java.util.ArrayList<>();
        for (AppraisalComment c : allComments) {
            if (c.getParentId() == null) {
                roots.add(c);
            } else {
                AppraisalComment root = findRoot(c, map);
                if (root != null) {
                    if (root.getReplies() == null) root.setReplies(new java.util.ArrayList<>());
                    root.getReplies().add(c);
                } else {
                    roots.add(c);
                }
            }
        }

        if ("hot".equalsIgnoreCase(sort)) {
            roots.sort((c1, c2) -> {
                Long l1 = c1.getLikeCount() == null ? 0L : c1.getLikeCount();
                Long l2 = c2.getLikeCount() == null ? 0L : c2.getLikeCount();
                int cmp = l2.compareTo(l1);
                if (cmp != 0) return cmp;
                return c2.getCreatedAt().compareTo(c1.getCreatedAt());
            });
        } else if ("desc".equalsIgnoreCase(sort)) {
            roots.sort((c1, c2) -> c2.getCreatedAt().compareTo(c1.getCreatedAt()));
        } else {
            roots.sort((c1, c2) -> c1.getCreatedAt().compareTo(c2.getCreatedAt()));
        }

        for (AppraisalComment r : roots) {
            if (r.getReplies() != null) r.getReplies().sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
        }

        return roots;
    }

    private AppraisalComment findRoot(AppraisalComment c, java.util.Map<Long, AppraisalComment> map) {
        AppraisalComment current = c;
        int depth = 0;
        while (current.getParentId() != null && depth < 100) {
            AppraisalComment parent = map.get(current.getParentId());
            if (parent == null) return null;
            current = parent;
            depth++;
        }
        return current;
    }
}
