package cn.iocoder.yudao.module.project.service.comment;

import java.util.Set;

public record CommentMentionEvent(Long tenantId, Long commentId, Long authorUserId,
                                         String targetType, Long targetId, String targetDisplayNo,
                                         String targetTitle, String contentPreview,
                                         Set<Long> recipientUserIds) {
}
