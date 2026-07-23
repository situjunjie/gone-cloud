package cn.iocoder.yudao.module.project.service.comment;

import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import cn.iocoder.yudao.module.project.enums.CommentTargetTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_TARGET_NOT_EXISTS;

@Component
public class WorkItemCommentTargetResolver implements CommentTargetResolver {

    @Resource
    private ProjectWorkItemMapper workItemMapper;
    @Resource
    private ProjectMapper projectMapper;

    @Override
    public CommentTargetTypeEnum getType() {
        return CommentTargetTypeEnum.WORK_ITEM;
    }

    @Override
    public CommentTarget resolve(Long targetId) {
        if (targetId == null) {
            throw exception(PROJECT_COMMENT_TARGET_NOT_EXISTS);
        }
        ProjectWorkItemDO workItem = workItemMapper.selectById(targetId);
        if (workItem == null) {
            throw exception(PROJECT_COMMENT_TARGET_NOT_EXISTS);
        }
        ProjectDO project = projectMapper.selectById(workItem.getProjectId());
        if (project == null) {
            throw exception(PROJECT_COMMENT_TARGET_NOT_EXISTS);
        }
        return new CommentTarget(workItem.getId(), project.getId(), workItem.getWorkItemNo(), workItem.getTitle());
    }

}
