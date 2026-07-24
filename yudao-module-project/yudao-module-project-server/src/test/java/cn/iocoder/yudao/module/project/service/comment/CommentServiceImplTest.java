package cn.iocoder.yudao.module.project.service.comment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentRespVO;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentUpdateReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.comment.CommentDO;
import cn.iocoder.yudao.module.project.dal.dataobject.comment.CommentMentionDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectMemberDO;
import cn.iocoder.yudao.module.project.dal.mysql.comment.CommentMapper;
import cn.iocoder.yudao.module.project.dal.mysql.comment.CommentMentionMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMemberMapper;
import cn.iocoder.yudao.module.project.enums.CommentStatusEnum;
import cn.iocoder.yudao.module.project.enums.CommentTargetTypeEnum;
import cn.iocoder.yudao.module.project.enums.ProjectPermissionEnum;
import cn.iocoder.yudao.module.project.service.project.ProjectPermissionService;
import cn.iocoder.yudao.module.project.service.project.ProjectService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_ARCHIVED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_DELETE_DENIED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_HTML_NOT_ALLOWED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_MENTION_INVALID;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_MENTION_LIMIT_EXCEEDED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_REPLY_INVALID;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_STATUS_INVALID;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_TARGET_NOT_EXISTS;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_UPDATE_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentServiceImplTest extends BaseMockitoUnitTest {

    private static final Long TENANT_ID = 10L;
    private static final Long LOGIN_USER_ID = 100L;
    private static final Long PROJECT_ID = 200L;
    private static final Long TARGET_ID = 300L;

    @InjectMocks
    private CommentServiceImpl service;

    @Mock
    private CommentMapper commentMapper;
    @Mock
    private CommentMentionMapper commentMentionMapper;
    @Mock
    private ProjectMemberMapper projectMemberMapper;
    @Mock
    private CommentTargetService targetService;
    @Mock
    private ProjectService projectService;
    @Mock
    private ProjectPermissionService permissionService;
    @Mock
    private AdminUserApi adminUserApi;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CommentTarget target;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        LoginUser loginUser = new LoginUser();
        loginUser.setId(LOGIN_USER_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, "password", List.of()));
        target = new CommentTarget(TARGET_ID, PROJECT_ID, "WI-000300", "评论目标");
        lenient().when(targetService.resolve(CommentTargetTypeEnum.WORK_ITEM.getValue(), TARGET_ID))
                .thenReturn(target);
        lenient().when(commentMentionMapper.selectListIncludingDeleted(TENANT_ID, 1L))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void testCreateComment_normalizesMentionsAndPublishesEvent() {
        CommentCreateReqVO reqVO = createReq("请确认验收口径");
        reqVO.setMentionUserIds(Set.of(LOGIN_USER_ID, 101L, 102L));
        when(projectMemberMapper.selectListByProjectIdAndUserIds(eq(PROJECT_ID), anyCollection()))
                .thenReturn(List.of(member(101L), member(102L)));
        doAnswer(invocation -> {
            CommentDO comment = invocation.getArgument(0);
            comment.setId(1L);
            return 1;
        }).when(commentMapper).insert(any(CommentDO.class));

        assertEquals(1L, service.createComment(reqVO));

        verify(projectService).validateProjectUsable(PROJECT_ID);
        verify(permissionService).validatePermission(PROJECT_ID, LOGIN_USER_ID,
                ProjectPermissionEnum.COMMENT_CREATE.name());
        ArgumentCaptor<CommentMentionDO> relationCaptor = ArgumentCaptor.forClass(CommentMentionDO.class);
        verify(commentMentionMapper, times(2)).insert(relationCaptor.capture());
        assertEquals(Set.of(101L, 102L), relationCaptor.getAllValues().stream()
                .map(CommentMentionDO::getMentionedUserId).collect(Collectors.toSet()));
        ArgumentCaptor<CommentMentionEvent> eventCaptor = ArgumentCaptor.forClass(CommentMentionEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(Set.of(101L, 102L), eventCaptor.getValue().recipientUserIds());
        assertEquals(TENANT_ID, eventCaptor.getValue().tenantId());
    }

    @Test
    void testCreateComment_rejectsCrossTargetReply() {
        CommentCreateReqVO reqVO = createReq("回复内容");
        reqVO.setReplyToCommentId(9L);
        when(commentMapper.selectById(9L)).thenReturn(comment(9L, LOGIN_USER_ID,
                CommentStatusEnum.NORMAL.getValue()).setTargetId(999L));

        assertServiceException(() -> service.createComment(reqVO), PROJECT_COMMENT_REPLY_INVALID);

        verify(commentMapper, never()).insert(any(CommentDO.class));
    }

    @Test
    void testCreateComment_rejectsRawHtmlBeforeWrite() {
        CommentCreateReqVO reqVO = createReq("<script>alert(1)</script>");

        assertServiceException(() -> service.createComment(reqVO), PROJECT_COMMENT_HTML_NOT_ALLOWED);

        verify(targetService, never()).resolve(any(), any());
    }

    @Test
    void testCreateComment_authorDoesNotCountTowardMentionLimit() {
        Set<Long> mentionUserIds = LongStream.rangeClosed(101L, 120L).boxed().collect(Collectors.toSet());
        mentionUserIds.add(LOGIN_USER_ID);
        CommentCreateReqVO reqVO = createReq("二十人提醒");
        reqVO.setMentionUserIds(mentionUserIds);
        when(projectMemberMapper.selectListByProjectIdAndUserIds(eq(PROJECT_ID), anyCollection()))
                .thenReturn(mentionUserIds.stream().filter(id -> !id.equals(LOGIN_USER_ID)).map(this::member).toList());
        doAnswer(invocation -> {
            ((CommentDO) invocation.getArgument(0)).setId(1L);
            return 1;
        }).when(commentMapper).insert(any(CommentDO.class));

        assertEquals(1L, service.createComment(reqVO));
        verify(commentMentionMapper, times(20)).insert(any(CommentMentionDO.class));
    }

    @Test
    void testCreateComment_rejectsMoreThanTwentyRecipients() {
        CommentCreateReqVO reqVO = createReq("超出提醒上限");
        reqVO.setMentionUserIds(LongStream.rangeClosed(101L, 121L).boxed().collect(Collectors.toSet()));

        assertServiceException(() -> service.createComment(reqVO), PROJECT_COMMENT_MENTION_LIMIT_EXCEEDED);

        verify(projectMemberMapper, never()).selectListByProjectIdAndUserIds(any(), anyCollection());
    }

    @Test
    void testCreateComment_rejectsNonMemberMention() {
        CommentCreateReqVO reqVO = createReq("提醒非成员");
        reqVO.setMentionUserIds(Set.of(101L, 999L));
        when(projectMemberMapper.selectListByProjectIdAndUserIds(eq(PROJECT_ID), anyCollection()))
                .thenReturn(List.of(member(101L)));

        assertServiceException(() -> service.createComment(reqVO), PROJECT_COMMENT_MENTION_INVALID, 999L);
    }

    @Test
    void testCreateComment_rejectsArchivedProject() {
        CommentCreateReqVO reqVO = createReq("归档项目不可评论");
        doThrow(new cn.iocoder.yudao.framework.common.exception.ServiceException(
                PROJECT_ARCHIVED.getCode(), PROJECT_ARCHIVED.getMsg()))
                .when(projectService).validateProjectUsable(PROJECT_ID);

        assertServiceException(() -> service.createComment(reqVO), PROJECT_ARCHIVED);

        verify(commentMapper, never()).insert(any(CommentDO.class));
    }

    @Test
    void testUpdateComment_onlyAuthorCanUpdate() {
        when(commentMapper.selectById(1L)).thenReturn(comment(1L, 999L,
                CommentStatusEnum.NORMAL.getValue()));
        CommentUpdateReqVO reqVO = updateReq("修改内容");

        assertServiceException(() -> service.updateComment(reqVO), PROJECT_COMMENT_UPDATE_DENIED);

        verify(commentMapper, never()).updateContentByIdAndStatus(any(), any(), any(), any());
    }

    @Test
    void testUpdateComment_rejectsTargetMovedToAnotherProject() {
        when(commentMapper.selectById(1L)).thenReturn(comment(1L, LOGIN_USER_ID,
                CommentStatusEnum.NORMAL.getValue()));
        when(targetService.resolve(CommentTargetTypeEnum.WORK_ITEM.getValue(), TARGET_ID))
                .thenReturn(new CommentTarget(TARGET_ID, 999L, "WI-000300", "已移动目标"));

        assertServiceException(() -> service.updateComment(updateReq("修改内容")),
                PROJECT_COMMENT_TARGET_NOT_EXISTS);

        verify(projectService, never()).validateProjectUsable(any());
        verify(permissionService, never()).validatePermission(any(), any(), any());
    }

    @Test
    void testUpdateComment_publishesOnlyNewMentions() {
        when(commentMapper.selectById(1L)).thenReturn(comment(1L, LOGIN_USER_ID,
                CommentStatusEnum.NORMAL.getValue()));
        when(commentMapper.updateContentByIdAndStatus(eq(1L), eq(CommentStatusEnum.NORMAL.getValue()),
                eq("修改内容"), any(LocalDateTime.class))).thenReturn(1);
        when(projectMemberMapper.selectListByProjectIdAndUserIds(eq(PROJECT_ID), anyCollection()))
                .thenReturn(List.of(member(101L), member(102L)));
        CommentMentionDO existing = new CommentMentionDO();
        existing.setId(11L);
        existing.setCommentId(1L);
        existing.setMentionedUserId(101L);
        existing.setDeleted(false);
        when(commentMentionMapper.selectListIncludingDeleted(TENANT_ID, 1L)).thenReturn(List.of(existing));
        CommentUpdateReqVO reqVO = updateReq("修改内容");
        reqVO.setMentionUserIds(Set.of(101L, 102L));

        service.updateComment(reqVO);

        ArgumentCaptor<CommentMentionDO> relationCaptor = ArgumentCaptor.forClass(CommentMentionDO.class);
        verify(commentMentionMapper).insert(relationCaptor.capture());
        assertEquals(102L, relationCaptor.getValue().getMentionedUserId());
        ArgumentCaptor<CommentMentionEvent> eventCaptor = ArgumentCaptor.forClass(CommentMentionEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(Set.of(102L), eventCaptor.getValue().recipientUserIds());
    }

    @Test
    void testUpdateComment_rejectsDeletedComment() {
        when(commentMapper.selectById(1L)).thenReturn(comment(1L, LOGIN_USER_ID,
                CommentStatusEnum.DELETED.getValue()));

        assertServiceException(() -> service.updateComment(updateReq("修改内容")), PROJECT_COMMENT_STATUS_INVALID);
    }

    @Test
    void testDeleteComment_adminCanDeleteOthersAndClearsMentions() {
        when(commentMapper.selectById(1L)).thenReturn(comment(1L, 999L,
                CommentStatusEnum.NORMAL.getValue()));
        when(permissionService.isAdmin(PROJECT_ID, LOGIN_USER_ID)).thenReturn(true);
        when(commentMapper.markDeletedByIdAndStatus(eq(1L), eq(CommentStatusEnum.NORMAL.getValue()),
                eq(CommentStatusEnum.DELETED.getValue()), any(LocalDateTime.class))).thenReturn(1);

        service.deleteComment(1L);

        verify(commentMentionMapper).deleteListByCommentId(1L);
    }

    @Test
    void testDeleteComment_normalMemberCannotDeleteOthers() {
        when(commentMapper.selectById(1L)).thenReturn(comment(1L, 999L,
                CommentStatusEnum.NORMAL.getValue()));

        assertServiceException(() -> service.deleteComment(1L), PROJECT_COMMENT_DELETE_DENIED);

        verify(commentMapper, never()).markDeletedByIdAndStatus(any(), any(), any(), any());
    }

    @Test
    void testGetCommentPage_assemblesDeletedPlaceholderReplyAndUsersInBatches() {
        LocalDateTime created = LocalDateTime.of(2026, 7, 22, 10, 0);
        CommentDO normal = comment(2L, 101L, CommentStatusEnum.NORMAL.getValue());
        normal.setContent("已编辑正文");
        normal.setReplyToCommentId(5L);
        normal.setCreateTime(created);
        normal.setUpdateTime(created.plusMinutes(1));
        CommentDO deleted = comment(1L, 102L, CommentStatusEnum.DELETED.getValue());
        deleted.setContent(null);
        deleted.setCreateTime(created.minusMinutes(1));
        deleted.setUpdateTime(created);
        CommentDO reply = comment(5L, 103L, CommentStatusEnum.NORMAL.getValue());
        String replyContent = "a".repeat(121);
        reply.setContent(replyContent);
        CommentPageReqVO reqVO = pageReq();
        when(commentMapper.selectPage(reqVO, PROJECT_ID))
                .thenReturn(new PageResult<>(List.of(normal, deleted), 2L));
        CommentMentionDO mention = new CommentMentionDO();
        mention.setCommentId(2L);
        mention.setMentionedUserId(105L);
        when(commentMentionMapper.selectListByCommentIds(List.of(2L, 1L))).thenReturn(List.of(mention));
        when(commentMapper.selectByIds(Set.of(5L))).thenReturn(List.of(reply));
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of(
                101L, user(101L, "作者一"), 102L, user(102L, "作者二"), 103L, user(103L, "被回复作者")));

        PageResult<CommentRespVO> result = service.getCommentPage(reqVO);

        assertEquals(2L, result.getTotal());
        CommentRespVO first = result.getList().get(0);
        assertEquals("作者一", first.getAuthorName());
        assertEquals(List.of(105L), first.getMentionUserIds());
        assertTrue(first.getEdited());
        assertFalse(first.getDeleted());
        assertEquals("被回复作者", first.getReplyTo().getAuthorName());
        assertEquals(replyContent, first.getReplyTo().getContent());
        assertEquals("a".repeat(117) + "...", first.getReplyTo().getContentPreview());
        CommentRespVO second = result.getList().get(1);
        assertNull(second.getContent());
        assertEquals(List.of(), second.getMentionUserIds());
        assertTrue(second.getDeleted());
        verify(commentMentionMapper).selectListByCommentIds(List.of(2L, 1L));
        verify(commentMapper).selectByIds(Set.of(5L));
        verify(adminUserApi).getUserMap(anyCollection());
    }

    @Test
    void testGetCommentPage_deletedReplyDoesNotExposeContent() {
        CommentDO normal = comment(2L, 101L, CommentStatusEnum.NORMAL.getValue());
        normal.setReplyToCommentId(5L);
        CommentDO deletedReply = comment(5L, 103L, CommentStatusEnum.DELETED.getValue());
        deletedReply.setContent("删除前的正文");
        CommentPageReqVO reqVO = pageReq();
        when(commentMapper.selectPage(reqVO, PROJECT_ID))
                .thenReturn(new PageResult<>(List.of(normal), 1L));
        when(commentMentionMapper.selectListByCommentIds(List.of(2L))).thenReturn(List.of());
        when(commentMapper.selectByIds(Set.of(5L))).thenReturn(List.of(deletedReply));
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of(
                101L, user(101L, "回复作者"), 103L, user(103L, "被回复作者")));

        PageResult<CommentRespVO> result = service.getCommentPage(reqVO);

        CommentRespVO.Reply reply = result.getList().get(0).getReplyTo();
        assertTrue(reply.getDeleted());
        assertNull(reply.getContent());
        assertNull(reply.getContentPreview());
    }

    private CommentCreateReqVO createReq(String content) {
        CommentCreateReqVO reqVO = new CommentCreateReqVO();
        reqVO.setTargetType(CommentTargetTypeEnum.WORK_ITEM.getValue());
        reqVO.setTargetId(TARGET_ID);
        reqVO.setContent(content);
        return reqVO;
    }

    private CommentUpdateReqVO updateReq(String content) {
        CommentUpdateReqVO reqVO = new CommentUpdateReqVO();
        reqVO.setId(1L);
        reqVO.setContent(content);
        return reqVO;
    }

    private CommentPageReqVO pageReq() {
        CommentPageReqVO reqVO = new CommentPageReqVO();
        reqVO.setTargetType(CommentTargetTypeEnum.WORK_ITEM.getValue());
        reqVO.setTargetId(TARGET_ID);
        return reqVO;
    }

    private CommentDO comment(Long id, Long authorUserId, String status) {
        CommentDO comment = new CommentDO();
        comment.setId(id);
        comment.setProjectId(PROJECT_ID);
        comment.setTargetType(CommentTargetTypeEnum.WORK_ITEM.getValue());
        comment.setTargetId(TARGET_ID);
        comment.setAuthorUserId(authorUserId);
        comment.setContent("正文");
        comment.setStatus(status);
        return comment;
    }

    private ProjectMemberDO member(Long userId) {
        ProjectMemberDO member = new ProjectMemberDO();
        member.setProjectId(PROJECT_ID);
        member.setUserId(userId);
        return member;
    }

    private AdminUserRespDTO user(Long id, String nickname) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id);
        user.setNickname(nickname);
        return user;
    }

}
