package cn.iocoder.yudao.module.project.framework.security;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.project.controller.admin.comment.CommentController;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentUpdateReqVO;
import cn.iocoder.yudao.module.project.service.comment.CommentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentMethodSecurityTest {

    private AnnotationConfigApplicationContext context;
    private CommentController controller;
    private TestProjectSecurity projectSecurity;
    private CommentService commentService;
    private CommentCreateReqVO createReqVO;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        controller = context.getBean(CommentController.class);
        projectSecurity = context.getBean(TestProjectSecurity.class);
        commentService = context.getBean(CommentService.class);
        LoginUser loginUser = new LoginUser();
        loginUser.setId(10L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, "password", List.of()));
        createReqVO = new CommentCreateReqVO();
        createReqVO.setTargetType("WORK_ITEM");
        createReqVO.setTargetId(300L);
        createReqVO.setContent("评论正文");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (context != null) {
            context.close();
        }
    }

    @Test
    void testTargetPermissionFailureDeniesCreate() {
        assertThrows(AccessDeniedException.class, () -> controller.createComment(createReqVO));

        assertEquals(1, projectSecurity.targetPermissionChecks);
        assertEquals("WORK_ITEM", projectSecurity.lastTargetType);
        assertEquals(300L, projectSecurity.lastTargetId);
        assertEquals("COMMENT_CREATE", projectSecurity.lastPermissionCode);
        verify(commentService, never()).createComment(createReqVO);
    }

    @Test
    void testTargetPermissionAllowsCreate() {
        projectSecurity.permissionAllowed = true;
        when(commentService.createComment(createReqVO)).thenReturn(1L);

        assertDoesNotThrow(() -> controller.createComment(createReqVO));

        verify(commentService).createComment(createReqVO);
        assertEquals(1, projectSecurity.targetPermissionChecks);
        assertEquals("COMMENT_CREATE", projectSecurity.lastPermissionCode);
    }

    @Test
    void testPageUsesTargetQueryPermission() {
        projectSecurity.permissionAllowed = true;
        CommentPageReqVO pageReqVO = new CommentPageReqVO();
        pageReqVO.setTargetType("WORK_ITEM");
        pageReqVO.setTargetId(301L);

        assertDoesNotThrow(() -> controller.getCommentPage(pageReqVO));

        verify(commentService).getCommentPage(pageReqVO);
        assertEquals("WORK_ITEM", projectSecurity.lastTargetType);
        assertEquals(301L, projectSecurity.lastTargetId);
        assertEquals("COMMENT_QUERY", projectSecurity.lastPermissionCode);
    }

    @Test
    void testCommentPermissionFailureDeniesUpdate() {
        CommentUpdateReqVO updateReqVO = new CommentUpdateReqVO();
        updateReqVO.setId(401L);
        updateReqVO.setContent("更新后的评论");

        assertThrows(AccessDeniedException.class, () -> controller.updateComment(updateReqVO));

        assertEquals(1, projectSecurity.commentPermissionChecks);
        assertEquals(401L, projectSecurity.lastCommentId);
        assertEquals("COMMENT_UPDATE", projectSecurity.lastPermissionCode);
        verify(commentService, never()).updateComment(updateReqVO);
    }

    @Test
    void testDeleteUsesCommentDeletePermission() {
        projectSecurity.permissionAllowed = true;

        assertDoesNotThrow(() -> controller.deleteComment(402L));

        verify(commentService).deleteComment(402L);
        assertEquals(1, projectSecurity.commentPermissionChecks);
        assertEquals(402L, projectSecurity.lastCommentId);
        assertEquals("COMMENT_DELETE", projectSecurity.lastPermissionCode);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        CommentController commentController() {
            return new CommentController();
        }

        @Bean
        CommentService commentService() {
            return mock(CommentService.class);
        }

        @Bean("projectSecurity")
        TestProjectSecurity projectSecurity() {
            return new TestProjectSecurity();
        }

    }

    public static class TestProjectSecurity {

        private boolean permissionAllowed;
        private int targetPermissionChecks;
        private int commentPermissionChecks;
        private String lastTargetType;
        private Long lastTargetId;
        private Long lastCommentId;
        private String lastPermissionCode;

        public boolean hasCommentTargetPermission(String targetType, Long targetId, String permissionCode) {
            targetPermissionChecks++;
            lastTargetType = targetType;
            lastTargetId = targetId;
            lastPermissionCode = permissionCode;
            return permissionAllowed;
        }

        public boolean hasCommentPermission(Long commentId, String permissionCode) {
            commentPermissionChecks++;
            lastCommentId = commentId;
            lastPermissionCode = permissionCode;
            return permissionAllowed;
        }

    }

}
