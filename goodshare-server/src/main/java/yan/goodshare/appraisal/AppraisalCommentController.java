package yan.goodshare.appraisal;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import yan.goodshare.entity.AppraisalComment;

import java.util.List;

@RestController
@RequestMapping("/api/appraisals/{appraisalId}/comments")
public class AppraisalCommentController {

    private final AppraisalCommentService appraisalCommentService;

    public AppraisalCommentController(AppraisalCommentService appraisalCommentService) {
        this.appraisalCommentService = appraisalCommentService;
    }

    @PostMapping
    public ResponseEntity<?> createComment(@PathVariable Long appraisalId, @Valid @RequestBody AppraisalCommentRequest commentRequest) {
        try {
            AppraisalComment createdComment = appraisalCommentService.createComment(appraisalId, commentRequest);
            return ResponseEntity.ok(createdComment);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> listComments(@PathVariable Long appraisalId, @RequestParam(required = false, defaultValue = "") String sort) {
        try {
            List<AppraisalComment> comments = appraisalCommentService.getCommentsByAppraisalId(appraisalId, sort);
            return ResponseEntity.ok(comments);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}