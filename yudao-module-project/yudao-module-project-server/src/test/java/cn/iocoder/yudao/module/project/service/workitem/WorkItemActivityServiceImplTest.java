package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkItemActivityServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private WorkItemActivityServiceImpl service;

    @Mock
    private CommentService commentService;
    @Mock
    private WorkItemChangeLogService workItemChangeLogService;
    @Mock
    private AdminUserApi adminUserApi;

    @Test
    void testGetPage_mergesSourcesBeforeSlicingAndAddsOperationAvatar() {
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 24, 10, 0);
        List<CommentRespVO> comments = List.of(
                comment(10L, 100L, "评论人", "comment-avatar", baseTime.plusMinutes(4)),
                comment(9L, 100L, "评论人", "comment-avatar", baseTime.plusMinutes(2)));
        List<WorkItemChangeLogRespVO> operations = List.of(
                operation(20L, 200L, null, baseTime.plusMinutes(3)),
                operation(19L, 200L, null, baseTime.plusMinutes(1)));
        when(commentService.getCommentPage(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResult<>(comments, 7L));
        when(workItemChangeLogService.getTimelinePage(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResult<>(operations, 5L));
        when(adminUserApi.getUserMap(java.util.Set.of(200L)))
                .thenReturn(Map.of(200L, user(200L, "操作人", "operation-avatar")));
        WorkItemActivityPageReqVO reqVO = reqVO(2, 2);

        PageResult<WorkItemActivityRespVO> result = service.getPage(reqVO);

        assertEquals(12L, result.getTotal());
        assertEquals(List.of(9L, 19L), result.getList().stream()
                .map(WorkItemActivityRespVO::getSourceId).toList());
        assertEquals(WorkItemActivityTypeEnum.COMMENT.getValue(), result.getList().get(0).getType());
        assertEquals(WorkItemActivityTypeEnum.OPERATION.getValue(), result.getList().get(1).getType());
        assertEquals("操作人", result.getList().get(1).getOperatorName());
        assertEquals("operation-avatar", result.getList().get(1).getOperatorAvatar());
        assertEquals("操作人", result.getList().get(1).getOperation().getUserName());

        ArgumentCaptor<CommentPageReqVO> commentCaptor = ArgumentCaptor.forClass(CommentPageReqVO.class);
        verify(commentService).getCommentPage(commentCaptor.capture());
        assertEquals(CommentTargetTypeEnum.WORK_ITEM.getValue(), commentCaptor.getValue().getTargetType());
        assertEquals(1L, commentCaptor.getValue().getTargetId());
        assertEquals(1, commentCaptor.getValue().getPageNo());
        assertEquals(4, commentCaptor.getValue().getPageSize());
        ArgumentCaptor<WorkItemChangeLogPageReqVO> operationCaptor =
                ArgumentCaptor.forClass(WorkItemChangeLogPageReqVO.class);
        verify(workItemChangeLogService).getTimelinePage(operationCaptor.capture());
        assertEquals(1L, operationCaptor.getValue().getWorkItemId());
        assertEquals(1, operationCaptor.getValue().getPageNo());
        assertEquals(4, operationCaptor.getValue().getPageSize());
    }

    @Test
    void testGetPage_sameTimeUsesTypeAndSourceIdTieBreakers() {
        LocalDateTime createTime = LocalDateTime.of(2026, 7, 24, 10, 0);
        when(commentService.getCommentPage(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResult<>(List.of(
                        comment(10L, 100L, "评论人", null, createTime),
                        comment(11L, 100L, "评论人", null, createTime)), 2L));
        when(workItemChangeLogService.getTimelinePage(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResult<>(List.of(operation(99L, null, "系统", createTime)), 1L));

        PageResult<WorkItemActivityRespVO> result = service.getPage(reqVO(1, 10));

        assertEquals(List.of(11L, 10L, 99L), result.getList().stream()
                .map(WorkItemActivityRespVO::getSourceId).toList());
        assertEquals(List.of("COMMENT", "COMMENT", "OPERATION"), result.getList().stream()
                .map(WorkItemActivityRespVO::getType).toList());
        verify(adminUserApi, never()).getUserMap(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testGetPage_emptyAndOutOfRangeReturnsEmptyListWithTotal() {
        when(commentService.getCommentPage(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResult<>(List.of(comment(1L, 100L, "评论人", null,
                        LocalDateTime.of(2026, 7, 24, 10, 0))), 1L));
        when(workItemChangeLogService.getTimelinePage(org.mockito.ArgumentMatchers.any()))
                .thenReturn(PageResult.empty());

        PageResult<WorkItemActivityRespVO> result = service.getPage(reqVO(2, 1));

        assertEquals(1L, result.getTotal());
        assertEquals(List.of(), result.getList());
        assertNull(result.getList().stream().findFirst().orElse(null));
    }

    private WorkItemActivityPageReqVO reqVO(int pageNo, int pageSize) {
        WorkItemActivityPageReqVO reqVO = new WorkItemActivityPageReqVO();
        reqVO.setWorkItemId(1L);
        reqVO.setPageNo(pageNo);
        reqVO.setPageSize(pageSize);
        return reqVO;
    }

    private CommentRespVO comment(Long id, Long userId, String userName, String avatar, LocalDateTime createTime) {
        CommentRespVO comment = new CommentRespVO();
        comment.setId(id);
        comment.setAuthorUserId(userId);
        comment.setAuthorName(userName);
        comment.setAuthorAvatar(avatar);
        comment.setCreateTime(createTime);
        return comment;
    }

    private WorkItemChangeLogRespVO operation(Long id, Long userId, String userName, LocalDateTime createTime) {
        WorkItemChangeLogRespVO operation = new WorkItemChangeLogRespVO();
        operation.setId(id);
        operation.setUserId(userId);
        operation.setUserName(userName);
        operation.setCreateTime(createTime);
        return operation;
    }

    private AdminUserRespDTO user(Long id, String nickname, String avatar) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id);
        user.setNickname(nickname);
        user.setAvatar(avatar);
        return user;
    }

}
