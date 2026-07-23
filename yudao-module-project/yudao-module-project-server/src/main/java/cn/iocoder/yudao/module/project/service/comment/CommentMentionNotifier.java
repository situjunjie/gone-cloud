package cn.iocoder.yudao.module.project.service.comment;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Slf4j
public class CommentMentionNotifier {

    static final String TEMPLATE_CODE = "PROJECT_COMMENT_MENTION";

    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;
    @Resource
    private AdminUserApi adminUserApi;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMention(CommentMentionEvent event) {
        if (event == null || event.recipientUserIds() == null || event.recipientUserIds().isEmpty()) {
            return;
        }
        TenantUtils.execute(event.tenantId(), () -> sendNotifications(event));
    }

    private void sendNotifications(CommentMentionEvent event) {
        String authorName = "用户" + event.authorUserId();
        try {
            AdminUserRespDTO author = adminUserApi.getUser(event.authorUserId()).getCheckedData();
            if (author != null && author.getNickname() != null) {
                authorName = author.getNickname();
            }
        } catch (Exception ex) {
            log.warn("[sendNotifications][commentId({}) authorUserId({}) 查询作者失败，使用编号降级]",
                    event.commentId(), event.authorUserId(), ex);
        }
        Map<String, Object> templateParams = new LinkedHashMap<>();
        templateParams.put("authorName", authorName);
        templateParams.put("workItemNo", event.targetDisplayNo());
        templateParams.put("targetId", event.targetId());
        templateParams.put("contentPreview", event.contentPreview());
        for (Long recipientUserId : event.recipientUserIds()) {
            try {
                notifyMessageSendApi.sendSingleMessageToAdmin(new NotifySendSingleToUserReqDTO()
                        .setUserId(recipientUserId)
                        .setTemplateCode(TEMPLATE_CODE)
                        .setTemplateParams(templateParams)).checkError();
            } catch (Exception ex) {
                log.warn("[sendNotifications][commentId({}) targetType({}) targetId({}) recipientUserId({}) 发送失败]",
                        event.commentId(), event.targetType(), event.targetId(), recipientUserId, ex);
            }
        }
    }

}
