package cn.iocoder.yudao.module.project.controller.admin.workitem;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemQueryViewRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemQueryViewSaveReqVO;
import cn.iocoder.yudao.module.project.convert.workitem.ProjectWorkItemConvert;
import cn.iocoder.yudao.module.project.service.workitem.WorkItemQueryViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 工作项查询视图")
@RestController
@RequestMapping("/project/work-item/query-view")
@Validated
public class WorkItemQueryViewController {

    @Resource
    private WorkItemQueryViewService queryViewService;

    @PostMapping("/create")
    @Operation(summary = "创建工作项查询视图")
    @PreAuthorize("@ss.hasPermission('project:work-item:create')")
    public CommonResult<Long> createQueryView(@Valid @RequestBody WorkItemQueryViewSaveReqVO createReqVO) {
        return success(queryViewService.createQueryView(createReqVO, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新工作项查询视图")
    @PreAuthorize("@ss.hasPermission('project:work-item:update')")
    public CommonResult<Boolean> updateQueryView(@Valid @RequestBody WorkItemQueryViewSaveReqVO updateReqVO) {
        queryViewService.updateQueryView(updateReqVO, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除工作项查询视图")
    @Parameter(name = "id", description = "视图编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('project:work-item:delete')")
    public CommonResult<Boolean> deleteQueryView(@RequestParam("id") Long id) {
        queryViewService.deleteQueryView(id, getLoginUserId());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得工作项查询视图")
    @Parameter(name = "id", description = "视图编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('project:work-item:query')")
    public CommonResult<WorkItemQueryViewRespVO> getQueryView(@RequestParam("id") Long id) {
        return success(ProjectWorkItemConvert.INSTANCE.convert(queryViewService.getQueryView(id, getLoginUserId())));
    }

    @GetMapping("/get-by-code")
    @Operation(summary = "根据编码获得工作项查询视图")
    @PreAuthorize("@ss.hasPermission('project:work-item:query')")
    public CommonResult<WorkItemQueryViewRespVO> getQueryViewByCode(@RequestParam("scope") String scope,
                                                                    @RequestParam("code") String code) {
        return success(ProjectWorkItemConvert.INSTANCE.convert(
                queryViewService.getQueryViewByCode(scope, code, getLoginUserId())));
    }

    @GetMapping("/list")
    @Operation(summary = "获得工作项查询视图列表")
    @PreAuthorize("@ss.hasPermission('project:work-item:query')")
    public CommonResult<List<WorkItemQueryViewRespVO>> getQueryViewList() {
        return success(ProjectWorkItemConvert.INSTANCE.convertQueryViewList(
                queryViewService.getQueryViewList(getLoginUserId())));
    }

}
