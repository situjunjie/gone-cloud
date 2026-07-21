package cn.iocoder.yudao.module.project.framework.security;

import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectMemberDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectRoleDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldOptionDO;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMemberMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectRoleMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldOptionMapper;
import cn.iocoder.yudao.module.project.enums.ProjectRoleScopeEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldScopeEnum;
import cn.iocoder.yudao.module.project.service.project.ProjectPermissionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Component("projectSecurity")
public class ProjectSecurityFrameworkService {

    @Resource
    private ProjectPermissionService permissionService;
    @Resource
    private ProjectWorkItemMapper workItemMapper;
    @Resource
    private WorkItemFieldMapper fieldMapper;
    @Resource
    private WorkItemFieldOptionMapper fieldOptionMapper;
    @Resource
    private ProjectMemberMapper memberMapper;
    @Resource
    private ProjectRoleMapper roleMapper;

    public boolean hasPermission(Long projectId, String permissionCode) {
        return permissionService.hasPermission(projectId, getLoginUserId(), permissionCode);
    }

    public boolean isAdmin(Long projectId) {
        return permissionService.isAdmin(projectId, getLoginUserId());
    }

    public boolean hasWorkItemPermission(Long workItemId, String permissionCode) {
        if (workItemId == null) {
            return false;
        }
        ProjectWorkItemDO workItem = workItemMapper.selectById(workItemId);
        return workItem != null && hasPermission(workItem.getProjectId(), permissionCode);
    }

    public boolean hasFieldPermission(Long fieldId, String permissionCode) {
        if (fieldId == null) {
            return false;
        }
        WorkItemFieldDO field = fieldMapper.selectById(fieldId);
        return field != null && WorkItemFieldScopeEnum.PROJECT.getValue().equals(field.getScope())
                && hasPermission(field.getProjectId(), permissionCode);
    }

    public boolean hasFieldOptionPermission(Long optionId, String permissionCode) {
        if (optionId == null) {
            return false;
        }
        WorkItemFieldOptionDO option = fieldOptionMapper.selectById(optionId);
        return option != null && hasFieldPermission(option.getFieldId(), permissionCode);
    }

    public boolean isAdminByMemberId(Long memberId) {
        if (memberId == null) {
            return false;
        }
        ProjectMemberDO member = memberMapper.selectById(memberId);
        return member != null && isAdmin(member.getProjectId());
    }

    public boolean isAdminByRoleId(Long roleId) {
        if (roleId == null) {
            return false;
        }
        ProjectRoleDO role = roleMapper.selectById(roleId);
        return role != null && ProjectRoleScopeEnum.PROJECT.getValue().equals(role.getScope())
                && isAdmin(role.getProjectId());
    }

}
