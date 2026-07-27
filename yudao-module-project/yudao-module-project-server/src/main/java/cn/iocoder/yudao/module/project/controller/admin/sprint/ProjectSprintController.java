package cn.iocoder.yudao.module.project.controller.admin.sprint;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.project.controller.admin.sprint.vo.ProjectSprintCreateReqVO;
import cn.iocoder.yudao.module.project.service.sprint.ProjectSprintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

}
