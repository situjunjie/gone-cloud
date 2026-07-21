package cn.iocoder.yudao.module.project.controller.admin.project;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectPermissionRespVO;
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

@Tag(name = "管理后台 - 项目全局角色模板")
@RestController
@RequestMapping("/project/global-role")
@Validated
public class ProjectGlobalRoleController {

    @Resource
    private ProjectRoleService roleService;

    @GetMapping("/permission-list")
    @Operation(summary = "获得固定项目权限目录")
    @PreAuthorize("@ss.hasPermission('project:global-role:query')")
    public CommonResult<List<ProjectPermissionRespVO>> getPermissionList() {
        return success(roleService.getPermissionList());
    }

    @GetMapping("/list")
    @Operation(summary = "获得全局角色模板列表")
    @PreAuthorize("@ss.hasPermission('project:global-role:query')")
    public CommonResult<List<ProjectRoleRespVO>> getRoleList() {
        return success(roleService.getGlobalRoleList());
    }

    @PostMapping("/create")
    @Operation(summary = "创建全局自定义角色模板")
    @PreAuthorize("@ss.hasPermission('project:global-role:manage')")
    public CommonResult<Long> createRole(@Valid @RequestBody ProjectRoleCreateReqVO reqVO) {
        return success(roleService.createGlobalRole(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新全局自定义角色模板")
    @PreAuthorize("@ss.hasPermission('project:global-role:manage')")
    public CommonResult<Boolean> updateRole(@Valid @RequestBody ProjectRoleUpdateReqVO reqVO) {
        roleService.updateGlobalRole(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除全局自定义角色模板")
    @Parameter(name = "id", description = "角色编号", required = true)
    @PreAuthorize("@ss.hasPermission('project:global-role:manage')")
    public CommonResult<Boolean> deleteRole(@RequestParam("id") Long id) {
        roleService.deleteGlobalRole(id);
        return success(true);
    }

    @PutMapping("/assign-permissions")
    @Operation(summary = "分配全局角色模板权限")
    @PreAuthorize("@ss.hasPermission('project:global-role:manage')")
    public CommonResult<Boolean> assignPermissions(
            @Valid @RequestBody ProjectRoleAssignPermissionsReqVO reqVO) {
        roleService.assignGlobalRolePermissions(reqVO);
        return success(true);
    }

}
