package cn.iocoder.yudao.module.devops.service.pipeline.approval;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCancelReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineNodeTypeEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogLevelEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.PipelineApprovalContext;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.*;

/**
 * 流水线审批节点 Service 实现。
 */
@Service
@Validated
public class PipelineApprovalServiceImpl implements PipelineApprovalService {

    @Resource
    private PipelineRunMapper pipelineRunMapper;
    @Resource
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Resource
    private BpmProcessInstanceApi bpmProcessInstanceApi;

    @Override
    public boolean isApprovalBusinessKey(String businessKey) {
        return StrUtil.startWith(businessKey, BUSINESS_KEY_PREFIX);
    }

    @Override
    public void startApproval(PipelineRunDO run, PipelineSpec.Node node, Long userId) {
        PipelineRunLogDO existingLog = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(run.getId(), node.getId());
        if (existingLog != null && PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(existingLog.getStatus())) {
            return;
        }
        if (existingLog != null && PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(existingLog.getStatus())) {
            return;
        }
        String processDefinitionKey = requiredParam(node, "processDefinitionKey");
        String businessKey = buildBusinessKey(run.getId(), node.getId());
        Map<String, Object> variables = buildVariables(run, node);

        PipelineRunLogDO log = existingLog == null ? createApprovalLog(run, node, businessKey, variables) : existingLog;
        PipelineApprovalContext context = buildWaitingContext(processDefinitionKey, businessKey, variables,
                userId == null ? run.getTriggerUserId() : userId);
        String processInstanceId = bpmProcessInstanceApi.createProcessInstance(context.getStartedBy(),
                new BpmProcessInstanceCreateReqDTO()
                        .setProcessDefinitionKey(processDefinitionKey)
                        .setVariables(variables)
                        .setBusinessKey(businessKey)).getCheckedData();
        context.setProcessInstanceId(processInstanceId);
        log.setStatus(PipelineRunLogStatusEnum.WAITING_INPUT.getStatus());
        log.setSummary("等待审批：" + StrUtil.blankToDefault(node.getName(), "审批"));
        log.setContextJson(JsonUtils.toJsonString(context));
        log.setStartedAt(log.getStartedAt() == null ? LocalDateTime.now() : log.getStartedAt());
        pipelineRunLogMapper.updateById(log);
    }

    @Override
    public void cancelApproval(PipelineRunDO run, String nodeId, Long userId) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(run.getId(), nodeId);
        if (log == null || isTerminalStatus(log.getStatus())) {
            return;
        }
        PipelineApprovalContext context = parseContext(log);
        String message = "流水线已取消";
        // 取消关联的 BPM 流程实例（微服务下事件不会回传 devops，需主动取消并自行收尾）
        if (StrUtil.isNotBlank(context.getProcessInstanceId())) {
            try {
                bpmProcessInstanceApi.cancelProcessInstance(userId == null ? run.getTriggerUserId() : userId,
                        new BpmProcessInstanceCancelReqDTO().setId(context.getProcessInstanceId()).setReason(message));
            } catch (Exception ignored) {
                // 流程实例可能已结束或不存在，忽略以保证流水线取消流程继续
            }
        }
        context.setStatus(PipelineApprovalContext.STATUS_CANCELED);
        context.setReason(message);
        context.setFinishedAt(LocalDateTime.now());
        log.setStatus(PipelineRunLogStatusEnum.CANCELED.getStatus());
        log.setSummary(message);
        log.setFinishedAt(LocalDateTime.now());
        log.setContextJson(JsonUtils.toJsonString(context));
        pipelineRunLogMapper.updateById(log);
    }

    @Override
    public PipelineApprovalStatusHandleResult handleProcessInstanceStatus(BpmProcessInstanceStatusEvent event) {
        if (event == null || !isApprovalBusinessKey(event.getBusinessKey())) {
            return PipelineApprovalStatusHandleResult.ignored();
        }
        BusinessKeyParts keyParts = parseBusinessKey(event.getBusinessKey());
        PipelineRunDO run = TenantUtils.executeIgnore(() -> pipelineRunMapper.selectById(keyParts.pipelineRunId()));
        if (run == null) {
            throw exception(PIPELINE_RUN_NOT_EXISTS);
        }
        return TenantUtils.execute(run.getTenantId(), () -> handleProcessInstanceStatus0(event, run, keyParts.nodeId()));
    }

    private PipelineApprovalStatusHandleResult handleProcessInstanceStatus0(BpmProcessInstanceStatusEvent event,
                                                                            PipelineRunDO run, String nodeId) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(run.getId(), nodeId);
        if (log == null) {
            throw exception(PIPELINE_RUN_LOG_NOT_EXISTS);
        }
        if (PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(log.getStatus())) {
            return PipelineApprovalStatusHandleResult.handled(false, run);
        }
        if (PipelineRunLogStatusEnum.FAILED.getStatus().equals(log.getStatus())
                || PipelineRunLogStatusEnum.CANCELED.getStatus().equals(log.getStatus())) {
            return PipelineApprovalStatusHandleResult.handled(false, run);
        }
        PipelineApprovalContext context = parseContext(log);
        context.setProcessInstanceId(StrUtil.blankToDefault(context.getProcessInstanceId(), event.getId()));
        context.setBpmStatus(event.getStatus());
        context.setReason(event.getReason());
        context.setFinishedAt(LocalDateTime.now());
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(event.getStatus())) {
            markApprovalSuccess(log, context, event);
            return PipelineApprovalStatusHandleResult.handled(true, run);
        }
        if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(event.getStatus())) {
            markApprovalFailed(run, log, context, event);
            return PipelineApprovalStatusHandleResult.handled(false, run);
        }
        if (BpmProcessInstanceStatusEnum.CANCEL.getStatus().equals(event.getStatus())) {
            markApprovalCanceled(run, log, context, event);
            return PipelineApprovalStatusHandleResult.handled(false, run);
        }
        return PipelineApprovalStatusHandleResult.ignored();
    }

    private void markApprovalSuccess(PipelineRunLogDO log, PipelineApprovalContext context,
                                     BpmProcessInstanceStatusEvent event) {
        context.setStatus(PipelineApprovalContext.STATUS_APPROVED);
        log.setStatus(PipelineRunLogStatusEnum.SUCCESS.getStatus());
        log.setSummary("审批通过");
        log.setFinishedAt(LocalDateTime.now());
        log.setContextJson(JsonUtils.toJsonString(context));
        log.setResultJson(JsonUtils.toJsonString(event));
        log.setErrorMessage(null);
        pipelineRunLogMapper.updateById(log);
    }

    private void markApprovalFailed(PipelineRunDO run, PipelineRunLogDO log, PipelineApprovalContext context,
                                    BpmProcessInstanceStatusEvent event) {
        context.setStatus(PipelineApprovalContext.STATUS_REJECTED);
        String message = StrUtil.blankToDefault(event.getReason(), "审批不通过");
        log.setStatus(PipelineRunLogStatusEnum.FAILED.getStatus());
        log.setSummary(message);
        log.setErrorMessage(StrUtil.subPre(message, 1000));
        log.setFinishedAt(LocalDateTime.now());
        log.setContextJson(JsonUtils.toJsonString(context));
        log.setResultJson(JsonUtils.toJsonString(event));
        pipelineRunLogMapper.updateById(log);
        markRunStopped(run, PipelineRunStatusEnum.FAILED.getStatus(), message);
    }

    private void markApprovalCanceled(PipelineRunDO run, PipelineRunLogDO log, PipelineApprovalContext context,
                                      BpmProcessInstanceStatusEvent event) {
        context.setStatus(PipelineApprovalContext.STATUS_CANCELED);
        String message = StrUtil.blankToDefault(event.getReason(), "审批已取消");
        log.setStatus(PipelineRunLogStatusEnum.CANCELED.getStatus());
        log.setSummary(message);
        log.setFinishedAt(LocalDateTime.now());
        log.setContextJson(JsonUtils.toJsonString(context));
        log.setResultJson(JsonUtils.toJsonString(event));
        pipelineRunLogMapper.updateById(log);
        markRunStopped(run, PipelineRunStatusEnum.CANCELED.getStatus(), message);
    }

    private void markRunStopped(PipelineRunDO run, Integer status, String message) {
        PipelineRunDO update = new PipelineRunDO();
        update.setId(run.getId());
        update.setRunStatus(status);
        update.setFinishedAt(LocalDateTime.now());
        update.setErrorMessage(StrUtil.subPre(message, 1000));
        pipelineRunMapper.updateById(update);
    }

    private PipelineRunLogDO createApprovalLog(PipelineRunDO run, PipelineSpec.Node node, String businessKey,
                                               Map<String, Object> variables) {
        PipelineRunLogDO log = new PipelineRunLogDO();
        log.setPipelineRunId(run.getId());
        log.setTenantId(run.getTenantId());
        log.setNodeId(node.getId());
        log.setNodeType(PipelineNodeTypeEnum.APPROVAL.getType());
        log.setNodeName(StrUtil.blankToDefault(node.getName(), "审批"));
        log.setLogLevel(PipelineRunLogLevelEnum.NODE.getLevel());
        log.setStatus(PipelineRunLogStatusEnum.PENDING.getStatus());
        log.setSort(800);
        log.setStartedAt(LocalDateTime.now());
        log.setSummary("准备发起审批");
        log.setContextJson(JsonUtils.toJsonString(Map.of("businessKey", businessKey, "variables", variables)));
        pipelineRunLogMapper.insert(log);
        return log;
    }

    private PipelineApprovalContext buildWaitingContext(String processDefinitionKey, String businessKey,
                                                        Map<String, Object> variables,
                                                        Long startedBy) {
        PipelineApprovalContext context = new PipelineApprovalContext();
        context.setProcessDefinitionKey(processDefinitionKey);
        context.setBusinessKey(businessKey);
        context.setVariables(variables);
        context.setStartedBy(startedBy);
        context.setStartedAt(LocalDateTime.now());
        context.setStatus(PipelineApprovalContext.STATUS_WAITING);
        return context;
    }

    private Map<String, Object> buildVariables(PipelineRunDO run, PipelineSpec.Node node) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("pipelineRunId", run.getId());
        variables.put("pipelineDefinitionId", run.getDefinitionId());
        variables.put("pipelineVersionId", run.getDefinitionVersionId());
        variables.put("appId", run.getAppId());
        variables.put("applicationEnvId", run.getApplicationEnvId());
        variables.put("branchName", run.getBranchName());
        variables.put("commitSha", run.getCommitSha());
        variables.put("nodeId", node.getId());
        variables.put("nodeName", node.getName());
        variables.put("nodeType", node.getType());
        return variables;
    }

    private PipelineApprovalContext parseContext(PipelineRunLogDO log) {
        PipelineApprovalContext context = JsonUtils.parseObject(log.getContextJson(), PipelineApprovalContext.class);
        return context == null ? new PipelineApprovalContext() : context;
    }

    private boolean isTerminalStatus(String status) {
        return PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(status)
                || PipelineRunLogStatusEnum.FAILED.getStatus().equals(status)
                || PipelineRunLogStatusEnum.CANCELED.getStatus().equals(status);
    }

    private String requiredParam(PipelineSpec.Node node, String paramName) {
        Object value = node.getParams() == null ? null : node.getParams().get(paramName);
        if (value == null || StrUtil.isBlank(String.valueOf(value))) {
            throw exception(PIPELINE_NODE_PARAM_INVALID, paramName);
        }
        return String.valueOf(value);
    }

    private String buildBusinessKey(Long pipelineRunId, String nodeId) {
        return BUSINESS_KEY_PREFIX + pipelineRunId + ":" + nodeId;
    }

    private BusinessKeyParts parseBusinessKey(String businessKey) {
        String payload = StrUtil.removePrefix(businessKey, BUSINESS_KEY_PREFIX);
        int separatorIndex = payload.indexOf(':');
        if (separatorIndex <= 0 || separatorIndex == payload.length() - 1) {
            throw exception(PIPELINE_APPROVAL_BUSINESS_KEY_INVALID, businessKey);
        }
        try {
            return new BusinessKeyParts(Long.parseLong(payload.substring(0, separatorIndex)),
                    payload.substring(separatorIndex + 1));
        } catch (NumberFormatException ex) {
            throw exception(PIPELINE_APPROVAL_BUSINESS_KEY_INVALID, businessKey);
        }
    }

    private record BusinessKeyParts(Long pipelineRunId, String nodeId) {
    }

}
