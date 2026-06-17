package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.CodeMergeConflictDetailRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.CodeMergeConflictResolutionReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.CodeMergeConflictRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.job.PipelineRunJobDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.job.PipelineRunJobMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineRunJobStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogLevelEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.git.GitConflictContent;
import cn.iocoder.yudao.module.devops.framework.git.GitConflictDescriptor;
import cn.iocoder.yudao.module.devops.framework.git.GitFileResolution;
import cn.iocoder.yudao.module.devops.framework.git.GitWorkspaceService;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.CodeMergeConflictContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.CodeMergeContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.CodeMergeResolutionContext;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.*;

/**
 * DevOps 流水线执行 Service 实现。
 */
@Service
@Validated
public class PipelineExecutionServiceImpl implements PipelineExecutionService {

    private static final String CODE_MERGE_NODE_ID = "builtin.code_merge";
    private static final String CODE_MERGE_NODE_NAME = "代码合并";

    @Resource
    private PipelineRunMapper pipelineRunMapper;
    @Resource
    private PipelineRunJobMapper pipelineRunJobMapper;
    @Resource
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Resource
    private GitWorkspaceService gitWorkspaceService;
    @Resource
    private PipelineExecutionEngine pipelineExecutionEngine;
    @Resource
    private PipelineExecutionAsyncService pipelineExecutionAsyncService;
    @Resource
    private CodeMergeService codeMergeService;

    @Override
    @CacheEvict(value = RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN,
            key = "#root.target.getApplicationEnvIdByPipelineRunId(#pipelineRunId)")
    public void startCodeMerge(Long pipelineRunId, List<Long> changeIds, Long userId) {
        PipelineRunDO run = validatePipelineRunExists(pipelineRunId);
        PipelineRunLogDO log = codeMergeService.getOrCreateCodeMergeLog(run, CODE_MERGE_NODE_ID, CODE_MERGE_NODE_NAME);
        CodeMergeExecutionStatus status = codeMergeService.startCodeMerge(run, log, changeIds, userId);
        continuePipelineIfCodeMergeSuccess(run, log, status, userId);
    }

    @Override
    public List<PipelineRunLogRespVO> getRunLogs(Long pipelineRunId) {
        validatePipelineRunExists(pipelineRunId);
        return pipelineRunLogMapper.selectListByPipelineRunId(pipelineRunId).stream()
                .map(this::buildLogRespVO)
                .toList();
    }

    @Override
    public List<CodeMergeConflictRespVO> getCodeMergeConflicts(Long pipelineRunId) {
        PipelineRunLogDO log = validateCodeMergeLogExists(pipelineRunId);
        return parseCodeMergeContext(log).getConflicts().stream()
                .map(this::buildConflictRespVO)
                .toList();
    }

    @Override
    public CodeMergeConflictDetailRespVO getCodeMergeConflictDetail(Long pipelineRunId, String filePath) {
        PipelineRunLogDO log = validateCodeMergeLogExists(pipelineRunId);
        CodeMergeContext context = parseCodeMergeContext(log);
        CodeMergeConflictContext conflict = findConflict(context, filePath);
        GitConflictDescriptor descriptor = buildGitConflictDescriptor(conflict);
        GitConflictContent content = gitWorkspaceService.readConflictContent(context.getWorkspaceKey(), descriptor);
        CodeMergeConflictDetailRespVO respVO = new CodeMergeConflictDetailRespVO();
        copyConflict(respVO, conflict);
        respVO.setBaseContent(content.getBaseContent());
        respVO.setOursContent(content.getOursContent());
        respVO.setTheirsContent(content.getTheirsContent());
        respVO.setWorkingContent(content.getWorkingContent());
        CodeMergeResolutionContext resolution = findResolution(context, filePath);
        respVO.setResultContent(resolution == null ? content.getResultContent() : resolution.getResolvedContent());
        respVO.setContentTooLarge(content.getContentTooLarge());
        respVO.setSupportedActions(Boolean.TRUE.equals(conflict.getText())
                && CodeMergeConflictContext.STATUS_UNRESOLVED.equals(conflict.getStatus())
                ? List.of("ACCEPT_OURS", "ACCEPT_THEIRS", "ACCEPT_BOTH", "MANUAL") : List.of());
        return respVO;
    }

    @Override
    @CacheEvict(value = RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN,
            key = "#root.target.getApplicationEnvIdByPipelineRunId(#pipelineRunId)")
    public void saveCodeMergeConflictResolution(Long pipelineRunId, CodeMergeConflictResolutionReqVO reqVO, Long userId) {
        PipelineRunLogDO log = validateCodeMergeLogExists(pipelineRunId);
        CodeMergeContext context = parseCodeMergeContext(log);
        CodeMergeConflictContext conflict = findConflict(context, reqVO.getFilePath());
        if (!Boolean.TRUE.equals(conflict.getText()) || CodeMergeConflictContext.STATUS_UNSUPPORTED.equals(conflict.getStatus())) {
            throw exception(PIPELINE_CODE_MERGE_CONFLICT_UNSUPPORTED);
        }
        conflict.setStatus(CodeMergeConflictContext.STATUS_RESOLVED);
        context.getResolutions().removeIf(resolution -> Objects.equals(resolution.getFilePath(), reqVO.getFilePath()));
        CodeMergeResolutionContext resolution = new CodeMergeResolutionContext();
        resolution.setFilePath(reqVO.getFilePath());
        resolution.setResolutionType(reqVO.getResolutionType());
        resolution.setResolvedContent(reqVO.getResolvedContent());
        resolution.setComment(reqVO.getComment());
        resolution.setResolvedBy(userId);
        resolution.setResolvedAt(LocalDateTime.now());
        context.getResolutions().add(resolution);
        updateContext(log, context, "已保存冲突解决：" + reqVO.getFilePath(), PipelineRunLogStatusEnum.WAITING_INPUT.getStatus());
        createEventLog(log, "保存冲突解决：" + reqVO.getFilePath(), userId);
    }

    @Override
    @CacheEvict(value = RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN,
            key = "#root.target.getApplicationEnvIdByPipelineRunId(#pipelineRunId)")
    public void continueCodeMerge(Long pipelineRunId, Long userId) {
        PipelineRunDO run = validatePipelineRunExists(pipelineRunId);
        PipelineRunLogDO log = validateCodeMergeLogExists(pipelineRunId);
        CodeMergeContext context = parseCodeMergeContext(log);
        if (!PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(log.getStatus())) {
            throw exception(PIPELINE_RUN_LOG_STATE_INVALID);
        }
        if (context.getConflicts().stream().anyMatch(conflict ->
                CodeMergeConflictContext.STATUS_UNSUPPORTED.equals(conflict.getStatus()))) {
            throw exception(PIPELINE_CODE_MERGE_CONFLICT_UNSUPPORTED);
        }
        if (context.getConflicts().stream().anyMatch(conflict ->
                !CodeMergeConflictContext.STATUS_RESOLVED.equals(conflict.getStatus()))) {
            throw exception(PIPELINE_CODE_MERGE_CONFLICT_UNRESOLVED);
        }
        List<GitFileResolution> resolutions = context.getConflicts().stream()
                .map(conflict -> findResolution(context, conflict.getFilePath()))
                .filter(Objects::nonNull)
                .map(resolution -> new GitFileResolution(resolution.getFilePath(), resolution.getResolvedContent()))
                .toList();
        CodeMergeExecutionStatus status = codeMergeService.continueResolvedConflicts(run, log, context, resolutions, userId);
        continuePipelineIfCodeMergeSuccess(run, log, status, userId);
    }

    @Override
    @CacheEvict(value = RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN,
            key = "#root.target.getApplicationEnvIdByPipelineRunId(#pipelineRunId)")
    public void retryCurrentCodeMergeChange(Long pipelineRunId, Long userId) {
        PipelineRunDO run = validatePipelineRunExists(pipelineRunId);
        PipelineRunLogDO log = validateCodeMergeLogExists(pipelineRunId);
        CodeMergeContext context = parseCodeMergeContext(log);
        if (!PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(log.getStatus())) {
            throw exception(PIPELINE_RUN_LOG_STATE_INVALID);
        }
        CodeMergeExecutionStatus status = codeMergeService.retryCurrentChange(run, log, context, userId);
        continuePipelineIfCodeMergeSuccess(run, log, status, userId);
    }

    @Override
    @CacheEvict(value = RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN,
            key = "#root.target.getApplicationEnvIdByPipelineRunId(#pipelineRunId)")
    public void cancelRun(Long pipelineRunId, Long userId) {
        PipelineRunDO run = validatePipelineRunExists(pipelineRunId);
        codeMergeService.cancelCodeMergeIfActive(run, userId);
        // 派发取消事件给流水线引擎（引擎会取消运行中的执行 Shell 节点）
        pipelineExecutionEngine.cancel(run, userId);
        run.setRunStatus(PipelineRunStatusEnum.CANCELED.getStatus());
        run.setFinishedAt(LocalDateTime.now());
        pipelineRunMapper.updateById(run);
    }

    private void continuePipelineIfCodeMergeSuccess(PipelineRunDO run, PipelineRunLogDO log,
                                                    CodeMergeExecutionStatus status, Long userId) {
        if (status != CodeMergeExecutionStatus.SUCCESS) {
            return;
        }
        if (!isCodeMergeFullySuccessful(log)) {
            return;
        }
        markCodeMergeJobSuccess(log);
        pipelineExecutionAsyncService.resumePipelineAsync(run.getId(), userId);
    }

    private boolean isCodeMergeFullySuccessful(PipelineRunLogDO log) {
        if (log == null || !PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(log.getStatus())) {
            return false;
        }
        CodeMergeContext context = parseCodeMergeContext(log);
        boolean allItemsSuccessful = context.getItems().stream()
                .allMatch(item -> cn.iocoder.yudao.module.devops.service.pipeline.execution.context.CodeMergeItemContext
                        .STATUS_SUCCESS.equals(item.getStatus()));
        if (!allItemsSuccessful) {
            return false;
        }
        return pipelineRunLogMapper.selectListByParentId(log.getId()).stream()
                .filter(child -> PipelineRunLogLevelEnum.STEP.getLevel().equals(child.getLogLevel()))
                .noneMatch(child -> PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(child.getStatus()));
    }

    private void markCodeMergeJobSuccess(PipelineRunLogDO log) {
        if (log == null || StrUtil.isBlank(log.getJobId())) {
            return;
        }
        PipelineRunJobDO job = pipelineRunJobMapper.selectByPipelineRunIdAndJobId(log.getPipelineRunId(), log.getJobId());
        if (job == null || PipelineRunJobStatusEnum.SUCCESS.getStatus().equals(job.getStatus())) {
            return;
        }
        job.setStatus(PipelineRunJobStatusEnum.SUCCESS.getStatus());
        job.setSummary("任务执行成功");
        job.setErrorMessage(null);
        job.setFinishedAt(LocalDateTime.now());
        pipelineRunJobMapper.updateById(job);
    }

    private void createEventLog(PipelineRunLogDO parent, String summary, Long userId) {
        PipelineRunLogDO log = new PipelineRunLogDO();
        log.setPipelineRunId(parent.getPipelineRunId());
        log.setParentId(parent.getId());
        log.setNodeId(parent.getNodeId() + ".event");
        log.setNodeType(parent.getNodeType());
        log.setNodeName("事件");
        log.setLogLevel(PipelineRunLogLevelEnum.EVENT.getLevel());
        log.setStatus(PipelineRunLogStatusEnum.SUCCESS.getStatus());
        log.setSort(1000);
        log.setStartedAt(LocalDateTime.now());
        log.setFinishedAt(LocalDateTime.now());
        log.setSummary(summary);
        log.setContextJson(JsonUtils.toJsonString(Map.of("userId", userId)));
        pipelineRunLogMapper.insert(log);
    }

    private void updateContext(PipelineRunLogDO log, CodeMergeContext context, String summary, String status) {
        log.setContextJson(JsonUtils.toJsonString(context));
        log.setSummary(summary);
        log.setStatus(status);
        pipelineRunLogMapper.updateById(log);
    }

    private CodeMergeContext parseCodeMergeContext(PipelineRunLogDO log) {
        CodeMergeContext context = JsonUtils.parseObject(log.getContextJson(), CodeMergeContext.class);
        return context == null ? new CodeMergeContext() : context;
    }

    private PipelineRunLogRespVO buildLogRespVO(PipelineRunLogDO log) {
        PipelineRunLogRespVO respVO = new PipelineRunLogRespVO();
        respVO.setId(log.getId());
        respVO.setPipelineRunId(log.getPipelineRunId());
        respVO.setParentId(log.getParentId());
        respVO.setStageId(log.getStageId());
        respVO.setStageName(log.getStageName());
        respVO.setJobId(log.getJobId());
        respVO.setJobName(log.getJobName());
        respVO.setStepId(log.getStepId());
        respVO.setStepType(log.getStepType());
        respVO.setStepName(log.getStepName());
        respVO.setNodeId(log.getNodeId());
        respVO.setNodeType(log.getNodeType());
        respVO.setNodeName(log.getNodeName());
        respVO.setLogLevel(log.getLogLevel());
        respVO.setStatus(log.getStatus());
        respVO.setSort(log.getSort());
        respVO.setAttempt(log.getAttempt());
        respVO.setStartedAt(log.getStartedAt());
        respVO.setFinishedAt(log.getFinishedAt());
        respVO.setDurationMillis(log.getDurationMillis());
        respVO.setRuntimeType(log.getRuntimeType());
        respVO.setExecutorGroup(log.getExecutorGroup());
        respVO.setExecutorImage(log.getExecutorImage());
        respVO.setRuntimeId(log.getRuntimeId());
        respVO.setRuntimeName(log.getRuntimeName());
        respVO.setWorkspacePath(log.getWorkspacePath());
        respVO.setSummary(log.getSummary());
        respVO.setContext(JsonUtils.parseMap(log.getContextJson()));
        respVO.setResult(JsonUtils.parseMap(log.getResultJson()));
        respVO.setLogFileUrl(log.getLogFileUrl());
        respVO.setLogTruncated(log.getLogTruncated());
        respVO.setErrorMessage(log.getErrorMessage());
        return respVO;
    }

    private CodeMergeConflictRespVO buildConflictRespVO(CodeMergeConflictContext conflict) {
        CodeMergeConflictRespVO respVO = new CodeMergeConflictRespVO();
        copyConflict(respVO, conflict);
        return respVO;
    }

    private void copyConflict(CodeMergeConflictRespVO respVO, CodeMergeConflictContext conflict) {
        respVO.setFilePath(conflict.getFilePath());
        respVO.setConflictType(conflict.getConflictType());
        respVO.setStatus(conflict.getStatus());
        respVO.setText(conflict.getText());
        respVO.setContentSize(conflict.getContentSize());
        respVO.setLineCount(conflict.getLineCount());
        respVO.setCharset(conflict.getCharset());
        respVO.setUnsupportedReason(conflict.getUnsupportedReason());
    }

    private GitConflictDescriptor buildGitConflictDescriptor(CodeMergeConflictContext conflict) {
        GitConflictDescriptor descriptor = new GitConflictDescriptor();
        descriptor.setFilePath(conflict.getFilePath());
        descriptor.setConflictType(conflict.getConflictType());
        descriptor.setBaseBlobSha(conflict.getBaseBlobSha());
        descriptor.setOursBlobSha(conflict.getOursBlobSha());
        descriptor.setTheirsBlobSha(conflict.getTheirsBlobSha());
        descriptor.setText(conflict.getText());
        descriptor.setContentSize(conflict.getContentSize());
        descriptor.setLineCount(conflict.getLineCount());
        descriptor.setCharset(conflict.getCharset());
        descriptor.setUnsupportedReason(conflict.getUnsupportedReason());
        return descriptor;
    }

    private CodeMergeConflictContext findConflict(CodeMergeContext context, String filePath) {
        return context.getConflicts().stream()
                .filter(conflict -> Objects.equals(conflict.getFilePath(), filePath))
                .findFirst()
                .orElseThrow(() -> exception(PIPELINE_CODE_MERGE_CONFLICT_UNRESOLVED));
    }

    private CodeMergeResolutionContext findResolution(CodeMergeContext context, String filePath) {
        return context.getResolutions().stream()
                .filter(resolution -> Objects.equals(resolution.getFilePath(), filePath))
                .findFirst()
                .orElse(null);
    }

    private PipelineRunDO validatePipelineRunExists(Long id) {
        PipelineRunDO run = pipelineRunMapper.selectById(id);
        if (run == null) {
            throw exception(PIPELINE_RUN_NOT_EXISTS);
        }
        return run;
    }

    private PipelineRunLogDO validateCodeMergeLogExists(Long pipelineRunId) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeType(
                pipelineRunId, PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE);
        if (log == null) {
            log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeType(
                    pipelineRunId, PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE_LEGACY);
        }
        if (log == null) {
            throw exception(PIPELINE_RUN_LOG_NOT_EXISTS);
        }
        return log;
    }

    public Long getApplicationEnvIdByPipelineRunId(Long pipelineRunId) {
        PipelineRunDO run = pipelineRunMapper.selectById(pipelineRunId);
        return run == null ? null : run.getApplicationEnvId();
    }

}
