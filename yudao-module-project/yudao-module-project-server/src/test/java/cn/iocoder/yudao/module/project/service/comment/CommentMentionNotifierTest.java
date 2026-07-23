package cn.iocoder.yudao.module.project.service.comment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentMentionNotifierTest extends BaseMockitoUnitTest {

    @InjectMocks
    private CommentMentionNotifier notifier;

    @Mock
    private NotifyMessageSendApi notifyMessageSendApi;
    @Mock
    private AdminUserApi adminUserApi;

    @Test
    void testOnMention_sendsOneMessagePerRecipient() {
        AdminUserRespDTO author = new AdminUserRespDTO();
        author.setId(100L);
        author.setNickname("评论作者");
        when(adminUserApi.getUser(100L)).thenReturn(success(author));
        when(notifyMessageSendApi.sendSingleMessageToAdmin(any())).thenReturn(success(1L));

        notifier.onMention(event(Set.of(101L, 102L)));

        ArgumentCaptor<NotifySendSingleToUserReqDTO> captor =
                ArgumentCaptor.forClass(NotifySendSingleToUserReqDTO.class);
        verify(notifyMessageSendApi, times(2)).sendSingleMessageToAdmin(captor.capture());
        assertEquals(Set.of(101L, 102L), captor.getAllValues().stream()
                .map(NotifySendSingleToUserReqDTO::getUserId).collect(java.util.stream.Collectors.toSet()));
        captor.getAllValues().forEach(request -> {
            assertEquals(CommentMentionNotifier.TEMPLATE_CODE, request.getTemplateCode());
            assertEquals("评论作者", request.getTemplateParams().get("authorName"));
            assertEquals("WI-000300", request.getTemplateParams().get("workItemNo"));
            assertEquals(300L, request.getTemplateParams().get("targetId"));
        });
    }

    @Test
    void testOnMention_authorLookupAndOneRecipientFailureAreIsolated() {
        when(adminUserApi.getUser(100L)).thenThrow(new IllegalStateException("user rpc unavailable"));
        when(notifyMessageSendApi.sendSingleMessageToAdmin(any()))
                .thenThrow(new IllegalStateException("notify unavailable"))
                .thenReturn(success(2L));

        notifier.onMention(event(new java.util.LinkedHashSet<>(List.of(101L, 102L))));

        ArgumentCaptor<NotifySendSingleToUserReqDTO> captor =
                ArgumentCaptor.forClass(NotifySendSingleToUserReqDTO.class);
        verify(notifyMessageSendApi, times(2)).sendSingleMessageToAdmin(captor.capture());
        assertEquals("用户100", captor.getAllValues().get(0).getTemplateParams().get("authorName"));
    }

    @Test
    void testOnMention_emptyRecipientsDoesNothing() {
        notifier.onMention(event(Set.of()));

        verify(adminUserApi, never()).getUser(any());
        verify(notifyMessageSendApi, never()).sendSingleMessageToAdmin(any());
    }

    private CommentMentionEvent event(Set<Long> recipients) {
        return new CommentMentionEvent(10L, 1L, 100L, "WORK_ITEM", 300L,
                "WI-000300", "评论目标", "正文摘要", recipients);
    }

}
