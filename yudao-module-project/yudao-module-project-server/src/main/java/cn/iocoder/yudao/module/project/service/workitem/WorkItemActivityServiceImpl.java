package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemActivityPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemActivityRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemChangeLogPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemChangeLogRespVO;
import cn.iocoder.yudao.module.project.enums.CommentTargetTypeEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemActivityTypeEnum;
import cn.iocoder.yudao.module.project.service.comment.CommentService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Validated
public class WorkItemActivityServiceImpl implements WorkItemActivityService {

    private static final Comparator<WorkItemActivityRespVO> ACTIVITY_COMPARATOR = Comparator
            .comparing(WorkItemActivityRespVO::getCreateTime,
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparingInt(WorkItemActivityServiceImpl::typeRank)
            .thenComparing(WorkItemActivityRespVO::getSourceId,
                    Comparator.nullsLast(Comparator.reverseOrder()));

    @Resource
    private CommentService commentService;
    @Resource
    private WorkItemChangeLogService workItemChangeLogService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public PageResult<WorkItemActivityRespVO> getPage(WorkItemActivityPageReqVO reqVO) {
        int requiredCount = Math.toIntExact((long) reqVO.getPageNo() * reqVO.getPageSize());
        PageResult<CommentRespVO> commentPage = getCommentPrefix(reqVO.getWorkItemId(), requiredCount);
        PageResult<WorkItemChangeLogRespVO> operationPage = getOperationPrefix(reqVO.getWorkItemId(), requiredCount);

        Map<Long, AdminUserRespDTO> operationUserMap = getOperationUserMap(operationPage.getList());
        List<WorkItemActivityRespVO> activities = new ArrayList<>(
                commentPage.getList().size() + operationPage.getList().size());
        commentPage.getList().stream().map(this::fromComment).forEach(activities::add);
        operationPage.getList().stream().map(item -> fromOperation(item, operationUserMap)).forEach(activities::add);
        activities.sort(ACTIVITY_COMPARATOR);

        int fromIndex = Math.min((reqVO.getPageNo() - 1) * reqVO.getPageSize(), activities.size());
        int toIndex = Math.min(fromIndex + reqVO.getPageSize(), activities.size());
        long total = commentPage.getTotal() + operationPage.getTotal();
        return new PageResult<>(new ArrayList<>(activities.subList(fromIndex, toIndex)), total);
    }

    private PageResult<CommentRespVO> getCommentPrefix(Long workItemId, int requiredCount) {
        CommentPageReqVO reqVO = new CommentPageReqVO();
        reqVO.setTargetType(CommentTargetTypeEnum.WORK_ITEM.getValue());
        reqVO.setTargetId(workItemId);
        reqVO.setPageNo(1);
        reqVO.setPageSize(requiredCount);
        return commentService.getCommentPage(reqVO);
    }

    private PageResult<WorkItemChangeLogRespVO> getOperationPrefix(Long workItemId, int requiredCount) {
        WorkItemChangeLogPageReqVO reqVO = new WorkItemChangeLogPageReqVO();
        reqVO.setWorkItemId(workItemId);
        reqVO.setPageNo(1);
        reqVO.setPageSize(requiredCount);
        return workItemChangeLogService.getTimelinePage(reqVO);
    }

    private Map<Long, AdminUserRespDTO> getOperationUserMap(List<WorkItemChangeLogRespVO> operations) {
        Set<Long> userIds = new LinkedHashSet<>();
        operations.stream().map(WorkItemChangeLogRespVO::getUserId).filter(Objects::nonNull).forEach(userIds::add);
        return userIds.isEmpty() ? Map.of() : adminUserApi.getUserMap(userIds);
    }

    private WorkItemActivityRespVO fromComment(CommentRespVO comment) {
        return new WorkItemActivityRespVO()
                .setType(WorkItemActivityTypeEnum.COMMENT.getValue())
                .setSourceId(comment.getId())
                .setOperatorUserId(comment.getAuthorUserId())
                .setOperatorName(comment.getAuthorName())
                .setOperatorAvatar(comment.getAuthorAvatar())
                .setCreateTime(comment.getCreateTime())
                .setComment(comment);
    }

    private WorkItemActivityRespVO fromOperation(WorkItemChangeLogRespVO operation,
                                                   Map<Long, AdminUserRespDTO> userMap) {
        AdminUserRespDTO user = operation.getUserId() == null ? null : userMap.get(operation.getUserId());
        String userName = operation.getUserName() != null ? operation.getUserName()
                : user == null ? null : user.getNickname();
        operation.setUserName(userName);
        return new WorkItemActivityRespVO()
                .setType(WorkItemActivityTypeEnum.OPERATION.getValue())
                .setSourceId(operation.getId())
                .setOperatorUserId(operation.getUserId())
                .setOperatorName(userName)
                .setOperatorAvatar(user == null ? null : user.getAvatar())
                .setCreateTime(operation.getCreateTime())
                .setOperation(operation);
    }

    private static int typeRank(WorkItemActivityRespVO activity) {
        return WorkItemActivityTypeEnum.COMMENT.getValue().equals(activity.getType()) ? 0 : 1;
    }

}
