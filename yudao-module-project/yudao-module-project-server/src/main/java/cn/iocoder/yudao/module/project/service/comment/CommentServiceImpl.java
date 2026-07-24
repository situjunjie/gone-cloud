package cn.iocoder.yudao.module.project.service.comment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.string.StrUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
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
import cn.iocoder.yudao.module.project.enums.ProjectPermissionEnum;
import cn.iocoder.yudao.module.project.service.project.ProjectPermissionService;
import cn.iocoder.yudao.module.project.service.project.ProjectService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_CONTENT_INVALID;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_DELETE_DENIED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_HTML_NOT_ALLOWED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_MENTION_INVALID;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_MENTION_LIMIT_EXCEEDED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_NOT_EXISTS;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_REPLY_INVALID;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_STATUS_INVALID;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_TARGET_NOT_EXISTS;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_UPDATE_DENIED;

@Service
@Validated
@Slf4j
public class CommentServiceImpl implements CommentService {

    private static final int MAX_CONTENT_LENGTH = 10_000;
    private static final int MAX_MENTION_COUNT = 20;
    private static final int CONTENT_PREVIEW_LENGTH = 120;

    @Resource
    private CommentMapper commentMapper;
    @Resource
    private CommentMentionMapper commentMentionMapper;
    @Resource
    private ProjectMemberMapper projectMemberMapper;
    @Resource
    private CommentTargetService targetService;
    @Resource
    private ProjectService projectService;
    @Resource
    private ProjectPermissionService permissionService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createComment(CommentCreateReqVO reqVO) {
        Long loginUserId = Objects.requireNonNull(getLoginUserId());
        validateContent(reqVO.getContent());
        CommentTarget target = targetService.resolve(reqVO.getTargetType(), reqVO.getTargetId());
        projectService.validateProjectUsable(target.projectId());
        permissionService.validatePermission(target.projectId(), loginUserId, ProjectPermissionEnum.COMMENT_CREATE.name());
        validateReply(reqVO.getReplyToCommentId(), reqVO.getTargetType(), reqVO.getTargetId(), target.projectId());
        LinkedHashSet<Long> mentionUserIds = normalizeMentionUserIds(reqVO.getMentionUserIds(), loginUserId);
        validateMentionMembers(target.projectId(), mentionUserIds);

        CommentDO comment = new CommentDO();
        comment.setProjectId(target.projectId());
        comment.setTargetType(reqVO.getTargetType());
        comment.setTargetId(reqVO.getTargetId());
        comment.setReplyToCommentId(reqVO.getReplyToCommentId());
        comment.setAuthorUserId(loginUserId);
        comment.setContent(reqVO.getContent());
        comment.setStatus(CommentStatusEnum.NORMAL.getValue());
        commentMapper.insert(comment);
        Set<Long> newMentionUserIds = syncMentions(comment.getId(), mentionUserIds);
        publishMentionEvent(comment, target, newMentionUserIds);
        log.info("[createComment][commentId({}) projectId({}) targetType({}) targetId({}) authorUserId({})]",
                comment.getId(), target.projectId(), reqVO.getTargetType(), reqVO.getTargetId(), loginUserId);
        return comment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateComment(CommentUpdateReqVO reqVO) {
        Long loginUserId = Objects.requireNonNull(getLoginUserId());
        CommentDO comment = validateCommentExists(reqVO.getId());
        CommentTarget target = targetService.resolve(comment.getTargetType(), comment.getTargetId());
        validateCommentTarget(comment, target);
        projectService.validateProjectUsable(comment.getProjectId());
        permissionService.validatePermission(comment.getProjectId(), loginUserId,
                ProjectPermissionEnum.COMMENT_UPDATE.name());
        if (!Objects.equals(comment.getAuthorUserId(), loginUserId)) {
            throw exception(PROJECT_COMMENT_UPDATE_DENIED);
        }
        validateNormal(comment);
        validateContent(reqVO.getContent());
        LinkedHashSet<Long> mentionUserIds = normalizeMentionUserIds(reqVO.getMentionUserIds(), loginUserId);
        validateMentionMembers(target.projectId(), mentionUserIds);

        if (commentMapper.updateContentByIdAndStatus(comment.getId(), CommentStatusEnum.NORMAL.getValue(),
                reqVO.getContent(), LocalDateTime.now()) == 0) {
            throw exception(PROJECT_COMMENT_STATUS_INVALID);
        }
        comment.setContent(reqVO.getContent());
        Set<Long> newMentionUserIds = syncMentions(comment.getId(), mentionUserIds);
        publishMentionEvent(comment, target, newMentionUserIds);
        log.info("[updateComment][commentId({}) projectId({}) targetType({}) targetId({}) authorUserId({})]",
                comment.getId(), target.projectId(), comment.getTargetType(), comment.getTargetId(), loginUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long id) {
        Long loginUserId = Objects.requireNonNull(getLoginUserId());
        CommentDO comment = validateCommentExists(id);
        CommentTarget target = targetService.resolve(comment.getTargetType(), comment.getTargetId());
        validateCommentTarget(comment, target);
        projectService.validateProjectUsable(comment.getProjectId());
        permissionService.validatePermission(comment.getProjectId(), loginUserId,
                ProjectPermissionEnum.COMMENT_DELETE.name());
        validateNormal(comment);
        if (!Objects.equals(comment.getAuthorUserId(), loginUserId)
                && !permissionService.isAdmin(comment.getProjectId(), loginUserId)) {
            throw exception(PROJECT_COMMENT_DELETE_DENIED);
        }
        if (commentMapper.markDeletedByIdAndStatus(comment.getId(), CommentStatusEnum.NORMAL.getValue(),
                CommentStatusEnum.DELETED.getValue(), LocalDateTime.now()) == 0) {
            throw exception(PROJECT_COMMENT_STATUS_INVALID);
        }
        commentMentionMapper.deleteListByCommentId(comment.getId());
        log.info("[deleteComment][commentId({}) projectId({}) targetType({}) targetId({}) operatorUserId({})]",
                comment.getId(), target.projectId(), comment.getTargetType(), comment.getTargetId(), loginUserId);
    }

    @Override
    public PageResult<CommentRespVO> getCommentPage(CommentPageReqVO reqVO) {
        Long loginUserId = Objects.requireNonNull(getLoginUserId());
        CommentTarget target = targetService.resolve(reqVO.getTargetType(), reqVO.getTargetId());
        permissionService.validatePermission(target.projectId(), loginUserId, ProjectPermissionEnum.COMMENT_QUERY.name());
        PageResult<CommentDO> page = commentMapper.selectPage(reqVO, target.projectId());
        if (page.getList().isEmpty()) {
            return PageResult.empty(page.getTotal());
        }
        return new PageResult<>(assembleComments(page.getList()), page.getTotal());
    }

    private List<CommentRespVO> assembleComments(List<CommentDO> comments) {
        List<Long> commentIds = comments.stream().map(CommentDO::getId).toList();
        Map<Long, List<Long>> mentionMap = commentMentionMapper.selectListByCommentIds(commentIds).stream()
                .collect(Collectors.groupingBy(CommentMentionDO::getCommentId,
                        Collectors.mapping(CommentMentionDO::getMentionedUserId, Collectors.toList())));
        Set<Long> replyIds = comments.stream().map(CommentDO::getReplyToCommentId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, CommentDO> replyMap = replyIds.isEmpty() ? Collections.emptyMap()
                : commentMapper.selectByIds(replyIds).stream()
                .collect(Collectors.toMap(CommentDO::getId, Function.identity()));
        Set<Long> userIds = comments.stream().map(CommentDO::getAuthorUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        replyMap.values().stream().map(CommentDO::getAuthorUserId).forEach(userIds::add);
        Map<Long, AdminUserRespDTO> userMap = userIds.isEmpty() ? Collections.emptyMap() : adminUserApi.getUserMap(userIds);

        List<CommentRespVO> result = new ArrayList<>(comments.size());
        for (CommentDO comment : comments) {
            boolean deleted = CommentStatusEnum.DELETED.getValue().equals(comment.getStatus());
            CommentRespVO item = BeanUtils.toBean(comment, CommentRespVO.class);
            AdminUserRespDTO author = userMap.get(comment.getAuthorUserId());
            item.setAuthorName(author == null ? null : author.getNickname());
            item.setAuthorAvatar(author == null ? null : author.getAvatar());
            item.setContent(deleted ? null : comment.getContent());
            item.setMentionUserIds(deleted ? List.of() : sorted(mentionMap.get(comment.getId())));
            item.setDeleted(deleted);
            item.setEdited(!deleted && comment.getCreateTime() != null && comment.getUpdateTime() != null
                    && comment.getUpdateTime().isAfter(comment.getCreateTime()));
            item.setReplyTo(buildReply(comment.getReplyToCommentId(), replyMap, userMap));
            result.add(item);
        }
        return result;
    }

    private CommentRespVO.Reply buildReply(Long replyToCommentId, Map<Long, CommentDO> replyMap,
                                                   Map<Long, AdminUserRespDTO> userMap) {
        if (replyToCommentId == null) {
            return null;
        }
        CommentDO replyComment = replyMap.get(replyToCommentId);
        if (replyComment == null) {
            return new CommentRespVO.Reply().setId(replyToCommentId).setDeleted(true);
        }
        boolean deleted = CommentStatusEnum.DELETED.getValue().equals(replyComment.getStatus());
        AdminUserRespDTO author = userMap.get(replyComment.getAuthorUserId());
        return new CommentRespVO.Reply()
                .setId(replyComment.getId())
                .setAuthorUserId(replyComment.getAuthorUserId())
                .setAuthorName(author == null ? null : author.getNickname())
                .setContent(deleted ? null : replyComment.getContent())
                .setContentPreview(deleted ? null : StrUtils.maxLength(replyComment.getContent(), CONTENT_PREVIEW_LENGTH))
                .setDeleted(deleted);
    }

    private List<Long> sorted(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userIds.stream().distinct().sorted(Comparator.naturalOrder()).toList();
    }

    private void validateContent(String content) {
        if (StrUtil.isBlank(content) || content.length() > MAX_CONTENT_LENGTH) {
            throw exception(PROJECT_COMMENT_CONTENT_INVALID);
        }
        if (!Jsoup.parseBodyFragment(content).body().children().isEmpty()) {
            throw exception(PROJECT_COMMENT_HTML_NOT_ALLOWED);
        }
    }

    private void validateReply(Long replyToCommentId, String targetType, Long targetId, Long projectId) {
        if (replyToCommentId == null) {
            return;
        }
        CommentDO reply = commentMapper.selectById(replyToCommentId);
        if (reply == null || !Objects.equals(reply.getProjectId(), projectId)
                || !Objects.equals(reply.getTargetType(), targetType)
                || !Objects.equals(reply.getTargetId(), targetId)
                || !CommentStatusEnum.NORMAL.getValue().equals(reply.getStatus())) {
            throw exception(PROJECT_COMMENT_REPLY_INVALID);
        }
    }

    private LinkedHashSet<Long> normalizeMentionUserIds(Collection<Long> userIds, Long authorUserId) {
        LinkedHashSet<Long> result = userIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(userIds);
        if (result.remove(null)) {
            throw exception(PROJECT_COMMENT_MENTION_INVALID, (Object) null);
        }
        result.remove(authorUserId);
        if (result.size() > MAX_MENTION_COUNT) {
            throw exception(PROJECT_COMMENT_MENTION_LIMIT_EXCEEDED);
        }
        return result;
    }

    private void validateMentionMembers(Long projectId, Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        Set<Long> memberUserIds = projectMemberMapper.selectListByProjectIdAndUserIds(projectId, userIds).stream()
                .map(ProjectMemberDO::getUserId).collect(Collectors.toSet());
        for (Long userId : userIds) {
            if (!memberUserIds.contains(userId)) {
                throw exception(PROJECT_COMMENT_MENTION_INVALID, userId);
            }
        }
    }

    private Set<Long> syncMentions(Long commentId, Set<Long> desiredUserIds) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Map<Long, CommentMentionDO> allRelations = commentMentionMapper
                .selectListIncludingDeleted(tenantId, commentId).stream()
                .collect(Collectors.toMap(CommentMentionDO::getMentionedUserId, Function.identity()));
        Set<Long> activeUserIds = allRelations.values().stream()
                .filter(item -> !Boolean.TRUE.equals(item.getDeleted()))
                .map(CommentMentionDO::getMentionedUserId).collect(Collectors.toSet());
        Set<Long> removedUserIds = new LinkedHashSet<>(activeUserIds);
        removedUserIds.removeAll(desiredUserIds);
        commentMentionMapper.deleteListByCommentIdAndUserIds(commentId, removedUserIds);

        Set<Long> addedUserIds = new LinkedHashSet<>(desiredUserIds);
        addedUserIds.removeAll(activeUserIds);
        LocalDateTime now = LocalDateTime.now();
        for (Long userId : addedUserIds) {
            CommentMentionDO existing = allRelations.get(userId);
            if (existing != null) {
                commentMentionMapper.restoreById(existing.getId(), tenantId, now);
            } else {
                CommentMentionDO relation = new CommentMentionDO();
                relation.setCommentId(commentId);
                relation.setMentionedUserId(userId);
                commentMentionMapper.insert(relation);
            }
        }
        return addedUserIds;
    }

    private CommentDO validateCommentExists(Long id) {
        CommentDO comment = commentMapper.selectById(id);
        if (comment == null) {
            throw exception(PROJECT_COMMENT_NOT_EXISTS);
        }
        return comment;
    }

    private void validateNormal(CommentDO comment) {
        if (!CommentStatusEnum.NORMAL.getValue().equals(comment.getStatus())) {
            throw exception(PROJECT_COMMENT_STATUS_INVALID);
        }
    }

    private void validateCommentTarget(CommentDO comment, CommentTarget target) {
        if (!Objects.equals(comment.getProjectId(), target.projectId())) {
            throw exception(PROJECT_COMMENT_TARGET_NOT_EXISTS);
        }
    }

    private void publishMentionEvent(CommentDO comment, CommentTarget target,
                                     Set<Long> recipientUserIds) {
        if (recipientUserIds.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new CommentMentionEvent(
                TenantContextHolder.getRequiredTenantId(), comment.getId(), comment.getAuthorUserId(),
                comment.getTargetType(), comment.getTargetId(), target.displayNo(), target.title(),
                StrUtils.maxLength(comment.getContent(), CONTENT_PREVIEW_LENGTH), Set.copyOf(recipientUserIds)));
    }

}
