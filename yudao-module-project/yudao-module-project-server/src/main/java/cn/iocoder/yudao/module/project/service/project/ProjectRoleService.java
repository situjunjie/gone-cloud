package cn.iocoder.yudao.module.project.service.project;

import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectPermissionRespVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectRoleAssignPermissionsReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectRoleCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectRoleRespVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectRoleUpdateReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectRoleDO;

import java.util.List;
import java.util.Map;

public interface ProjectRoleService {

    List<ProjectPermissionRespVO> getPermissionList();

    void ensureTenantBuiltinRoles();

    Map<String, Long> snapshotGlobalRoles(Long projectId);

    Long createGlobalRole(ProjectRoleCreateReqVO reqVO);

    Long createProjectRole(ProjectRoleCreateReqVO reqVO);

    void updateGlobalRole(ProjectRoleUpdateReqVO reqVO);

    void updateProjectRole(ProjectRoleUpdateReqVO reqVO);

    void deleteGlobalRole(Long id);

    void deleteProjectRole(Long id);

    void assignGlobalRolePermissions(ProjectRoleAssignPermissionsReqVO reqVO);

    void assignProjectRolePermissions(ProjectRoleAssignPermissionsReqVO reqVO);

    List<ProjectRoleRespVO> getGlobalRoleList();

    List<ProjectRoleRespVO> getProjectRoleList(Long projectId);

    ProjectRoleDO validateProjectRole(Long id, Long projectId);

    ProjectRoleDO getProjectBuiltinRole(Long projectId, String builtinType);

}
