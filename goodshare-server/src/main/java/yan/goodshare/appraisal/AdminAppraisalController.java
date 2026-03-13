package yan.goodshare.appraisal;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;
import yan.goodshare.entity.Appraisal;

@RestController
@RequestMapping("/api/admin/appraisals")
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class AdminAppraisalController {

    private final AppraisalService appraisalService;

    public AdminAppraisalController(AppraisalService appraisalService) {
        this.appraisalService = appraisalService;
    }
    // 获取所有评价（管理员）
    @GetMapping
    public Page<Appraisal> list(@RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "10") int size) {
        return appraisalService.getAllAppraisalsForAdmin(page, size);
    }
    // 删除评价（管理员）
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        appraisalService.deleteAppraisal(id);
    }
}
