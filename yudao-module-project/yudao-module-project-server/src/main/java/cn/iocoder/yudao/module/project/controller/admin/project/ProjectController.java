package cn.iocoder.yudao.module.project.controller.admin.project;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectArchiveReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMemberAddReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMemberRespVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMemberUpdateRoleReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectRespVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectSaveReqVO;
import cn.iocoder.yudao.module.project.convert.project.ProjectConvert;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.project.service.project.ProjectService;
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

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 项目")
@RestController
@RequestMapping("/project/project")
@Validated
public class ProjectController {

    @Resource
    private ProjectService projectService;

    @PostMapping("/create")
    @Operation(summary = "创建项目")
    @PreAuthorize("@ss.hasPermission('project:project:create')")
    public CommonResult<Long> createProject(@Valid @RequestBody ProjectSaveReqVO createReqVO) {
        return success(projectService.createProject(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目")
    @PreAuthorize("@ss.hasPermission('project:project:update')")
    public CommonResult<Boolean> updateProject(@Valid @RequestBody ProjectSaveReqVO updateReqVO) {
        projectService.updateProject(updateReqVO);
        return success(true);
    }

    @PutMapping("/archive")
    @Operation(summary = "归档项目")
    @PreAuthorize("@ss.hasPermission('project:project:archive')")
    public CommonResult<Boolean> archiveProject(@Valid @RequestBody ProjectArchiveReqVO archiveReqVO) {
        projectService.archiveProject(archiveReqVO.getId());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得项目")
    @Parameter(name = "id", description = "项目编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('project:project:query')")
    @TransMethodResult
    public CommonResult<ProjectRespVO> getProject(@RequestParam("id") Long id) {
        ProjectRespVO respVO = ProjectConvert.INSTANCE.convert(projectService.getProject(id));
        if (respVO != null) {
            respVO.setMemberCount(projectService.getProjectMemberCount(id).intValue());
        }
        return success(respVO);
    }

    @GetMapping("/page")
    @Operation(summary = "获得项目分页")
    @PreAuthorize("@ss.hasPermission('project:project:query')")
    @TransMethodResult
    public CommonResult<PageResult<ProjectRespVO>> getProjectPage(@Valid ProjectPageReqVO pageReqVO) {
        PageResult<ProjectDO> pageResult = projectService.getProjectPage(pageReqVO);
        PageResult<ProjectRespVO> respPage = ProjectConvert.INSTANCE.convertPage(pageResult);
        respPage.getList().forEach(project -> project.setMemberCount(
                projectService.getProjectMemberCount(project.getId()).intValue()));
        return success(respPage);
    }

    @PostMapping("/member/add")
    @Operation(summary = "新增项目成员")
    @PreAuthorize("@ss.hasPermission('project:project:member')")
    public CommonResult<Long> addProjectMember(@Valid @RequestBody ProjectMemberAddReqVO addReqVO) {
        return success(projectService.addProjectMember(addReqVO));
    }

    @PutMapping("/member/update-role")
    @Operation(summary = "修改项目成员角色")
    @PreAuthorize("@ss.hasPermission('project:project:member')")
    public CommonResult<Boolean> updateProjectMemberRole(
            @Valid @RequestBody ProjectMemberUpdateRoleReqVO updateReqVO) {
        projectService.updateProjectMemberRole(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/member/remove")
    @Operation(summary = "移除项目成员")
    @Parameter(name = "id", description = "项目成员编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('project:project:member')")
    public CommonResult<Boolean> removeProjectMember(@RequestParam("id") Long id) {
        projectService.removeProjectMember(id);
        return success(true);
    }

    @GetMapping("/member-list")
    @Operation(summary = "获得项目成员列表")
    @Parameter(name = "projectId", description = "项目编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('project:project:query')")
    @TransMethodResult
    public CommonResult<List<ProjectMemberRespVO>> getProjectMemberList(@RequestParam("projectId") Long projectId) {
        return success(ProjectConvert.INSTANCE.convertMemberList(projectService.getProjectMemberList(projectId)));
    }

}
