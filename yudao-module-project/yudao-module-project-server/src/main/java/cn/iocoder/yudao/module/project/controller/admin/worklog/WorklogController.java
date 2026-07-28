package cn.iocoder.yudao.module.project.controller.admin.worklog;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogRespVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogSummaryReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogSummaryRespVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogUpdateReqVO;
import cn.iocoder.yudao.module.project.service.worklog.WorklogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.dromara.core.trans.anno.TransMethodResult;
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

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 项目工时")
@RestController
@RequestMapping("/project/worklog")
@Validated
public class WorklogController {

    @Resource
    private WorklogService worklogService;

    @PostMapping("/create")
    @Operation(summary = "创建项目工时")
    @PreAuthorize("@ss.hasPermission('project:worklog:create') and "
            + "(@projectSecurity.hasWorkItemPermission(#createReqVO.workItemId, 'WORKLOG_CREATE') or "
            + "@projectSecurity.hasWorkItemPermission(#createReqVO.workItemId, 'WORKLOG_MANAGE'))")
    public CommonResult<Long> createWorklog(@Valid @RequestBody WorklogCreateReqVO createReqVO) {
        return success(worklogService.createWorklog(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目工时")
    @PreAuthorize("@ss.hasPermission('project:worklog:update') and "
            + "@projectSecurity.canManageWorklog(#updateReqVO.id)")
    public CommonResult<Boolean> updateWorklog(@Valid @RequestBody WorklogUpdateReqVO updateReqVO) {
        worklogService.updateWorklog(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目工时")
    @Parameter(name = "id", description = "工时编号", required = true)
    @PreAuthorize("@ss.hasPermission('project:worklog:delete') and @projectSecurity.canManageWorklog(#id)")
    public CommonResult<Boolean> deleteWorklog(@RequestParam("id") Long id) {
        worklogService.deleteWorklog(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得项目工时")
    @Parameter(name = "id", description = "工时编号", required = true)
    @PreAuthorize("@ss.hasPermission('project:worklog:query') and "
            + "@projectSecurity.hasWorklogPermission(#id, 'WORKLOG_QUERY')")
    @TransMethodResult
    public CommonResult<WorklogRespVO> getWorklog(@RequestParam("id") Long id) {
        return success(worklogService.getWorklog(id));
    }

    @PostMapping("/page")
    @Operation(summary = "获得项目工时分页")
    @PreAuthorize("@ss.hasPermission('project:worklog:query') and "
            + "@projectSecurity.hasPermission(#pageReqVO.projectId, 'WORKLOG_QUERY')")
    @TransMethodResult
    public CommonResult<PageResult<WorklogRespVO>> getWorklogPage(
            @Valid @RequestBody WorklogPageReqVO pageReqVO) {
        return success(worklogService.getWorklogPage(pageReqVO));
    }

    @PostMapping("/summary")
    @Operation(summary = "汇总项目工时")
    @PreAuthorize("@ss.hasPermission('project:worklog:query') and "
            + "@projectSecurity.hasPermission(#reqVO.projectId, 'WORKLOG_QUERY')")
    public CommonResult<WorklogSummaryRespVO> getWorklogSummary(
            @Valid @RequestBody WorklogSummaryReqVO reqVO) {
        return success(worklogService.getWorklogSummary(reqVO));
    }

}
