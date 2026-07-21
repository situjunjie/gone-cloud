package cn.iocoder.yudao.module.project.controller.admin.project;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectRoleAssignPermissionsReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectRoleCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectRoleRespVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectRoleUpdateReqVO;
import cn.iocoder.yudao.module.project.service.project.ProjectRoleService;
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

@Tag(name = "管理后台 - 项目角色")
@RestController
@RequestMapping("/project/role")
@Validated
public class ProjectRoleController {

    @Resource
    private ProjectRoleService roleService;

    @GetMapping("/list")
    @Operation(summary = "获得项目角色列表")
    @PreAuthorize("@ss.hasPermission('project:project:role') and @projectSecurity.isAdmin(#projectId)")
    public CommonResult<List<ProjectRoleRespVO>> getRoleList(@RequestParam("projectId") Long projectId) {
        return success(roleService.getProjectRoleList(projectId));
    }

    @PostMapping("/create")
    @Operation(summary = "创建项目自定义角色")
    @PreAuthorize("@ss.hasPermission('project:project:role') and @projectSecurity.isAdmin(#reqVO.projectId)")
    public CommonResult<Long> createRole(@Valid @RequestBody ProjectRoleCreateReqVO reqVO) {
        return success(roleService.createProjectRole(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目自定义角色")
    @PreAuthorize("@ss.hasPermission('project:project:role') and @projectSecurity.isAdminByRoleId(#reqVO.id)")
    public CommonResult<Boolean> updateRole(@Valid @RequestBody ProjectRoleUpdateReqVO reqVO) {
        roleService.updateProjectRole(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目自定义角色")
    @Parameter(name = "id", description = "角色编号", required = true)
    @PreAuthorize("@ss.hasPermission('project:project:role') and @projectSecurity.isAdminByRoleId(#id)")
    public CommonResult<Boolean> deleteRole(@RequestParam("id") Long id) {
        roleService.deleteProjectRole(id);
        return success(true);
    }

    @PutMapping("/assign-permissions")
    @Operation(summary = "分配项目角色权限")
    @PreAuthorize("@ss.hasPermission('project:project:role') and @projectSecurity.isAdminByRoleId(#reqVO.roleId)")
    public CommonResult<Boolean> assignPermissions(
            @Valid @RequestBody ProjectRoleAssignPermissionsReqVO reqVO) {
        roleService.assignProjectRolePermissions(reqVO);
        return success(true);
    }

}
