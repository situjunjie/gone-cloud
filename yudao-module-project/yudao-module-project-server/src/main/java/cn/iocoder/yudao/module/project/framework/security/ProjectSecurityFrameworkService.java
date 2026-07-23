package cn.iocoder.yudao.module.project.framework.security;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.project.dal.dataobject.comment.CommentDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectMemberDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectRoleDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldOptionDO;
import cn.iocoder.yudao.module.project.dal.mysql.comment.CommentMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMemberMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectRoleMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldOptionMapper;
import cn.iocoder.yudao.module.project.enums.ProjectRoleScopeEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldScopeEnum;
import cn.iocoder.yudao.module.project.service.project.ProjectPermissionService;
import cn.iocoder.yudao.module.project.service.comment.CommentTarget;
import cn.iocoder.yudao.module.project.service.comment.CommentTargetService;
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
    private ProjectRoleMapper projectRoleMapper;
    @Resource
    private CommentMapper commentMapper;
    @Resource
    private CommentTargetService commentTargetService;

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

    public boolean hasCommentTargetPermission(String targetType, Long targetId, String permissionCode) {
        try {
            CommentTarget target = commentTargetService.resolve(targetType, targetId);
            return hasPermission(target.projectId(), permissionCode);
        } catch (ServiceException ignored) {
            return false;
        }
    }

    public boolean hasCommentPermission(Long commentId, String permissionCode) {
        if (commentId == null) {
            return false;
        }
        CommentDO comment = commentMapper.selectById(commentId);
        return comment != null && hasPermission(comment.getProjectId(), permissionCode);
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
        ProjectRoleDO role = projectRoleMapper.selectById(roleId);
        return role != null && ProjectRoleScopeEnum.PROJECT.getValue().equals(role.getScope())
                && isAdmin(role.getProjectId());
    }

}
