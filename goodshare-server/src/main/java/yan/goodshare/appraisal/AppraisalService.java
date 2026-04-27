package yan.goodshare.appraisal;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yan.goodshare.entity.Appraisal;
import yan.goodshare.entity.AppraisalVote;
import yan.goodshare.entity.User;
import yan.goodshare.mapper.AppraisalMapper;
import yan.goodshare.mapper.AppraisalVoteMapper;
import yan.goodshare.mapper.UserMapper;
import yan.goodshare.mapper.AppraisalCommentMapper;

import java.time.LocalDateTime;

@Service
public class AppraisalService {

    private final AppraisalMapper appraisalMapper;
    private final AppraisalVoteMapper appraisalVoteMapper;
    private final AppraisalCommentMapper appraisalCommentMapper;
    private final UserMapper userMapper;

    public AppraisalService(AppraisalMapper appraisalMapper, AppraisalVoteMapper appraisalVoteMapper, AppraisalCommentMapper appraisalCommentMapper, UserMapper userMapper) {
        this.appraisalMapper = appraisalMapper;
        this.appraisalVoteMapper = appraisalVoteMapper;
        this.appraisalCommentMapper = appraisalCommentMapper;
        this.userMapper = userMapper;
    }

    public Page<Appraisal> getAppraisals(int page, int size, Long currentUserId) {
        Page<Appraisal> pageParam = new Page<>(page, size);
        QueryWrapper<Appraisal> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 0) // Only normal status
                   .orderByDesc("created_at");
        
        Page<Appraisal> result = appraisalMapper.selectPage(pageParam, queryWrapper);
        
        for (Appraisal appraisal : result.getRecords()) {
            populateAppraisalDetails(appraisal, currentUserId);
        }
        
        return result;
    }

    public Page<Appraisal> getUserAppraisals(int page, int size, Long userId) {
        Page<Appraisal> pageParam = new Page<>(page, size);
        QueryWrapper<Appraisal> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                   .orderByDesc("created_at");
        
        Page<Appraisal> result = appraisalMapper.selectPage(pageParam, queryWrapper);
        
        // Populate user info (though it's the same user) and votes
        for (Appraisal appraisal : result.getRecords()) {
            populateAppraisalDetails(appraisal, userId);
        }
        
        return result;
    }

    public Appraisal getAppraisalDetail(Long id, Long currentUserId) {
        Appraisal appraisal = appraisalMapper.selectById(id);
        if (appraisal == null) return null;
        populateAppraisalDetails(appraisal, currentUserId);
        return appraisal;
    }

    private void populateAppraisalDetails(Appraisal appraisal, Long currentUserId) {
        // Fill User info
        User user = userMapper.selectById(appraisal.getUserId());
        if (user != null) {
            user.setPassword(null); // Hide password
            appraisal.setUser(user);
        }

        // Fill Current User Vote
        if (currentUserId != null) {
            AppraisalVote vote = appraisalVoteMapper.selectOne(new QueryWrapper<AppraisalVote>()
                    .eq("appraisal_id", appraisal.getId())
                    .eq("user_id", currentUserId));
            if (vote != null) {
                appraisal.setCurrentUserVote(vote.getVoteType());
            } else {
                appraisal.setCurrentUserVote(0);
            }
        }
    }

    @Transactional
    public Appraisal createAppraisal(Long userId, String productName, String description, String images) {
        Appraisal appraisal = new Appraisal();
        appraisal.setUserId(userId);
        appraisal.setProductName(productName);
        appraisal.setDescription(description);
        appraisal.setImages(images);
        appraisal.setStatus(0);
        appraisal.setRealVotes(0);
        appraisal.setFakeVotes(0);
        appraisal.setCreatedAt(LocalDateTime.now());
        
        appraisalMapper.insert(appraisal);
        return appraisal;
    }

    @Transactional
    public void vote(Long userId, Long appraisalId, Integer voteType) {
        Appraisal appraisal = appraisalMapper.selectById(appraisalId);
        if (appraisal == null) {
            throw new RuntimeException("Appraisal not found");
        }

        // Check if already voted
        AppraisalVote existingVote = appraisalVoteMapper.selectOne(new QueryWrapper<AppraisalVote>()
                .eq("appraisal_id", appraisalId)
                .eq("user_id", userId));

        if (existingVote != null) {
            // If vote type is same, do nothing (or toggle off? let's assume no toggle off for now)
            if (existingVote.getVoteType().equals(voteType)) {
                return;
            }
            
            // Change vote
            // Decrement old vote count
            if (existingVote.getVoteType() == 1) {
                appraisal.setRealVotes(Math.max(0, appraisal.getRealVotes() - 1));
            } else {
                appraisal.setFakeVotes(Math.max(0, appraisal.getFakeVotes() - 1));
            }
            
            // Update vote
            existingVote.setVoteType(voteType);
            appraisalVoteMapper.updateById(existingVote);
        } else {
            // New vote
            AppraisalVote newVote = new AppraisalVote();
            newVote.setAppraisalId(appraisalId);
            newVote.setUserId(userId);
            newVote.setVoteType(voteType);
            newVote.setCreatedAt(LocalDateTime.now());
            appraisalVoteMapper.insert(newVote);
        }

        // Increment new vote count
        if (voteType == 1) {
            appraisal.setRealVotes(appraisal.getRealVotes() + 1);
        } else {
            appraisal.setFakeVotes(appraisal.getFakeVotes() + 1);
        }
        
        appraisalMapper.updateById(appraisal);
    }

    // Admin methods
    public Page<Appraisal> getAllAppraisalsForAdmin(int page, int size) {
        Page<Appraisal> pageParam = new Page<>(page, size);
        QueryWrapper<Appraisal> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("created_at");
        Page<Appraisal> result = appraisalMapper.selectPage(pageParam, queryWrapper);
        
        for (Appraisal appraisal : result.getRecords()) {
            User user = userMapper.selectById(appraisal.getUserId());
            if (user != null) {
                user.setPassword(null);
                appraisal.setUser(user);
            }
        }
        return result;
    }

    // Admin delete (hard delete)
    public void deleteAppraisal(Long id) {
        appraisalMapper.deleteById(id);
        // Also delete votes
        appraisalVoteMapper.delete(new QueryWrapper<AppraisalVote>().eq("appraisal_id", id));
        // Also delete comments
        appraisalCommentMapper.deleteByAppraisalId(id);
    }

    // User delete (soft delete)
    public void deleteAppraisal(Long id, Long userId) {
        Appraisal appraisal = appraisalMapper.selectById(id);
        if (appraisal == null) {
            throw new RuntimeException("Appraisal not found");
        }
        if (!appraisal.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }
        deleteAppraisal(id);
    }
}
