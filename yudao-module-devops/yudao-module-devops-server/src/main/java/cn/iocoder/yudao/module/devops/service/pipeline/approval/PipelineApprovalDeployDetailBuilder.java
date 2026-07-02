package cn.iocoder.yudao.module.devops.service.pipeline.approval;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.environment.EnvironmentMapper;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.PipelineRunChangeSnapshotContext;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 流水线审批发布详情 HTML 构建器。
 */
@Slf4j
@Component
public class PipelineApprovalDeployDetailBuilder {

    private static final String EMPTY_VALUE = "-";

    @Resource
    private ApplicationMapper applicationMapper;
    @Resource
    private ApplicationEnvMapper applicationEnvMapper;
    @Resource
    private EnvironmentMapper environmentMapper;
    @Resource
    private ChangeMapper changeMapper;
    @Resource
    private AdminUserApi adminUserApi;

    public String buildDeployHtml(PipelineRunDO run) {
        ApplicationDO application = run.getAppId() == null ? null : applicationMapper.selectById(run.getAppId());
        ApplicationEnvDO applicationEnv = run.getApplicationEnvId() == null
                ? null : applicationEnvMapper.selectById(run.getApplicationEnvId());
        EnvironmentDO environment = applicationEnv == null || applicationEnv.getEnvId() == null
                ? null : environmentMapper.selectById(applicationEnv.getEnvId());
        List<DeployChange> changes = buildDeployChanges(run);
        Map<Long, AdminUserRespDTO> userMap = getUserMap(run, changes);

        StringBuilder html = new StringBuilder(2048);
        html.append("<h3>发布审批详情</h3>");
        appendApplicationSection(html, run, application);
        appendEnvironmentSection(html, run, applicationEnv, environment);
        appendPipelineSection(html, run, userMap);
        appendChangeSection(html, changes, userMap);
        return html.toString();
    }

    private void appendApplicationSection(StringBuilder html, PipelineRunDO run, ApplicationDO application) {
        html.append("<h4>应用信息</h4>");
        html.append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"6\"><tbody>");
        appendRow(html, "应用名称", application == null ? null : application.getName());
        appendRow(html, "应用标识", application == null ? null : application.getAppKey());
        appendRow(html, "应用编号", run.getAppId());
        html.append("</tbody></table>");
    }

    private void appendEnvironmentSection(StringBuilder html, PipelineRunDO run, ApplicationEnvDO applicationEnv,
                                          EnvironmentDO environment) {
        html.append("<h4>环境信息</h4>");
        html.append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"6\"><tbody>");
        appendRow(html, "环境名称", environment == null ? null : environment.getEnvName());
        appendRow(html, "环境标识", environment == null ? null : environment.getEnvKey());
        appendRow(html, "环境阶段", environment == null ? null : environment.getEnvStage());
        appendRow(html, "环境编号", applicationEnv == null ? null : applicationEnv.getEnvId());
        appendRow(html, "应用环境编号", run.getApplicationEnvId());
        html.append("</tbody></table>");
    }

    private void appendPipelineSection(StringBuilder html, PipelineRunDO run, Map<Long, AdminUserRespDTO> userMap) {
        html.append("<h4>流水线信息</h4>");
        html.append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"6\"><tbody>");
        appendRow(html, "运行编号", run.getId());
        appendRow(html, "部署分支", run.getBranchName());
        appendRow(html, "提交 SHA", run.getCommitSha());
        appendRow(html, "触发人", displayUser(run.getTriggerUserId(), userMap));
        appendRow(html, "触发时间", formatDateTime(run.getTriggeredAt()));
        html.append("</tbody></table>");
    }

    private void appendChangeSection(StringBuilder html, List<DeployChange> changes, Map<Long, AdminUserRespDTO> userMap) {
        html.append("<h4>变更列表</h4>");
        if (CollUtil.isEmpty(changes)) {
            html.append("<p>本次发布无关联变更。</p>");
            return;
        }
        html.append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"6\"><thead><tr>");
        appendHeader(html, "变更标识");
        appendHeader(html, "标题");
        appendHeader(html, "分支");
        appendHeader(html, "本次发布 commit");
        appendHeader(html, "最新 commit");
        appendHeader(html, "提交信息");
        appendHeader(html, "测试人");
        appendHeader(html, "代码审核人");
        html.append("</tr></thead><tbody>");
        for (DeployChange deployChange : changes) {
            ChangeDO change = deployChange.change();
            html.append("<tr>");
            appendCell(html, change == null ? deployChange.changeId() : change.getChangeKey());
            appendCell(html, change == null ? null : change.getTitle());
            appendCell(html, change == null ? null : change.getBranchName());
            appendCell(html, deployChange.commitSha());
            appendCell(html, change == null ? null : change.getLatestCommitSha());
            appendCell(html, change == null ? null : change.getLatestCommitMessage());
            appendCell(html, change == null ? null : displayUser(change.getTesterUserId(), userMap));
            appendCell(html, change == null ? null : displayUser(change.getCodeReviewerUserId(), userMap));
            html.append("</tr>");
        }
        html.append("</tbody></table>");
    }

    private List<DeployChange> buildDeployChanges(PipelineRunDO run) {
        LinkedHashMap<Long, String> changeCommits = parseSnapshotChangeCommits(run);
        if (changeCommits.isEmpty() && run.getChangeId() != null) {
            changeCommits.put(run.getChangeId(), run.getCommitSha());
        }
        if (changeCommits.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChangeDO> changes = changeMapper.selectListByIds(changeCommits.keySet());
        Map<Long, ChangeDO> changeMap = CollUtil.emptyIfNull(changes).stream()
                .collect(Collectors.toMap(ChangeDO::getId, Function.identity(), (first, second) -> first));
        return changeCommits.entrySet().stream()
                .map(entry -> new DeployChange(entry.getKey(), entry.getValue(), changeMap.get(entry.getKey())))
                .toList();
    }

    private LinkedHashMap<Long, String> parseSnapshotChangeCommits(PipelineRunDO run) {
        List<PipelineRunChangeSnapshotContext> snapshots = JsonUtils.parseArray(run.getChangeSnapshotJson(),
                PipelineRunChangeSnapshotContext.class);
        LinkedHashMap<Long, String> changeCommits = new LinkedHashMap<>();
        for (PipelineRunChangeSnapshotContext snapshot : CollUtil.emptyIfNull(snapshots)) {
            if (snapshot.getChangeId() == null) {
                continue;
            }
            changeCommits.put(snapshot.getChangeId(), snapshot.getCommitSha());
        }
        return changeCommits;
    }

    private Map<Long, AdminUserRespDTO> getUserMap(PipelineRunDO run, List<DeployChange> changes) {
        Set<Long> userIds = new LinkedHashSet<>();
        if (run.getTriggerUserId() != null) {
            userIds.add(run.getTriggerUserId());
        }
        userIds.addAll(collectUserIds(changes, ChangeDO::getTesterUserId));
        userIds.addAll(collectUserIds(changes, ChangeDO::getCodeReviewerUserId));
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        try {
            Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(userIds);
            return userMap == null ? Collections.emptyMap() : userMap;
        } catch (Exception ex) {
            log.warn("[getUserMap][userIds({}) 查询审批发布详情用户失败: {}]", userIds, ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<Long> collectUserIds(List<DeployChange> changes, Function<ChangeDO, Long> getter) {
        return CollUtil.emptyIfNull(changes).stream()
                .map(DeployChange::change)
                .filter(change -> change != null)
                .map(getter)
                .filter(userId -> userId != null)
                .toList();
    }

    private String displayUser(Long userId, Map<Long, AdminUserRespDTO> userMap) {
        if (userId == null) {
            return EMPTY_VALUE;
        }
        AdminUserRespDTO user = userMap.get(userId);
        if (user != null && StrUtil.isNotBlank(user.getNickname())) {
            return user.getNickname();
        }
        return String.valueOf(userId);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : String.valueOf(dateTime);
    }

    private void appendRow(StringBuilder html, String label, Object value) {
        html.append("<tr><th>").append(escape(label)).append("</th><td>")
                .append(escape(value)).append("</td></tr>");
    }

    private void appendHeader(StringBuilder html, String label) {
        html.append("<th>").append(escape(label)).append("</th>");
    }

    private void appendCell(StringBuilder html, Object value) {
        html.append("<td>").append(escape(value)).append("</td>");
    }

    private String escape(Object value) {
        if (value == null || StrUtil.isBlank(String.valueOf(value))) {
            return EMPTY_VALUE;
        }
        return HtmlUtils.htmlEscape(String.valueOf(value));
    }

    private record DeployChange(Long changeId, String commitSha, ChangeDO change) {
    }

}
