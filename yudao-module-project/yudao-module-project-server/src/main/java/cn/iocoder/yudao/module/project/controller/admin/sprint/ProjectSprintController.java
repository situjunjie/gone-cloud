package cn.iocoder.yudao.module.project.controller.admin.sprint;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.project.controller.admin.sprint.vo.ProjectSprintCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.sprint.vo.ProjectSprintRespVO;
import cn.iocoder.yudao.module.project.controller.admin.sprint.vo.ProjectSprintSimpleRespVO;
import cn.iocoder.yudao.module.project.service.sprint.ProjectSprintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.dromara.core.trans.anno.TransMethodResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 项目迭代")
@RestController
@RequestMapping("/project/sprint")
@Validated
public class ProjectSprintController {

    @Resource
    private ProjectSprintService projectSprintService;

    @PostMapping("/create")
    @Operation(summary = "创建项目迭代")
    @PreAuthorize("@ss.hasPermission('project:sprint:create') and "
            + "@projectSecurity.hasPermission(#createReqVO.projectId, 'SPRINT_CREATE')")
    public CommonResult<Long> createSprint(@Valid @RequestBody ProjectSprintCreateReqVO createReqVO) {
        return success(projectSprintService.createSprint(createReqVO));
    }

    @GetMapping("/list")
    @Operation(summary = "获得项目迭代列表")
    @Parameter(name = "projectId", description = "项目编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('project:project:query') and "
            + "@projectSecurity.hasPermission(#projectId, 'PROJECT_QUERY')")
    @TransMethodResult
    public CommonResult<List<ProjectSprintRespVO>> getSprintList(
            @RequestParam("projectId") @NotNull(message = "项目编号不能为空") Long projectId) {
        return success(projectSprintService.getSprintList(projectId));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得项目迭代精简列表", description = "只返回迭代编号和名称，主要用于前端下拉选项")
    @Parameter(name = "projectId", description = "项目编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('project:project:query') and "
            + "@projectSecurity.hasPermission(#projectId, 'PROJECT_QUERY')")
    public CommonResult<List<ProjectSprintSimpleRespVO>> getSprintSimpleList(
            @RequestParam("projectId") @NotNull(message = "项目编号不能为空") Long projectId) {
        return success(projectSprintService.getSprintSimpleList(projectId));
    }

}
