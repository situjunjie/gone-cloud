package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.CodeMergeConflictDetailRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.CodeMergeConflictResolutionReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.CodeMergeConflictRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.environment.EnvironmentMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.MergeStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineNodeTypeEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogLevelEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderAuthTypeEnum;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderTypeEnum;
import cn.iocoder.yudao.module.devops.framework.git.GitCommandException;
import cn.iocoder.yudao.module.devops.framework.git.GitConflictContent;
import cn.iocoder.yudao.module.devops.framework.git.GitConflictDescriptor;
import cn.iocoder.yudao.module.devops.framework.git.GitFileResolution;
import cn.iocoder.yudao.module.devops.framework.git.GitMergeResult;
import cn.iocoder.yudao.module.devops.framework.git.GitWorkspacePrepareResult;
import cn.iocoder.yudao.module.devops.framework.git.GitWorkspaceService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.CodeMergeConflictContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.CodeMergeContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.CodeMergeItemContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.CodeMergeResolutionContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.CodeMergeResultContext;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Resource
    private ApplicationMapper applicationMapper;
    @Resource
    private ApplicationEnvMapper applicationEnvMapper;
    @Resource
    private EnvironmentMapper environmentMapper;
    @Resource
    private ChangeMapper changeMapper;
    @Resource
    private ChangeEnvMapper changeEnvMapper;
    @Resource
    private RepositoryProviderService repositoryProviderService;
    @Resource
    private GitWorkspaceService gitWorkspaceService;

    @Override
    public void startCodeMerge(Long pipelineRunId, List<Long> changeIds, Long userId) {
        PipelineRunDO run = validatePipelineRunExists(pipelineRunId);
        validateNoOtherActiveRun(run);
        PipelineRunLogDO log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeType(
                pipelineRunId, PipelineNodeTypeEnum.CODE_MERGE.getType());
        if (log != null) {
            return;
        }
        log = createNodeLog(run.getId(), PipelineRunLogStatusEnum.RUNNING.getStatus(), "开始代码合并");
        try {
            executeCodeMerge(run, log, changeIds, userId);
        } catch (GitCommandException ex) {
            failRunAndLog(run, log, sanitizeGitOutput(ex.getMessage() + ": " + ex.getOutput(), null));
        } catch (Exception ex) {
            failRunAndLog(run, log, StrUtil.subPre(ex.getMessage(), 1000));
        }
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
        String mergeCommitSha = gitWorkspaceService.continueMerge(context.getWorkspaceKey(), resolutions,
                "Resolve conflicts for change " + context.getCurrentChangeId());
        CodeMergeItemContext currentItem = findCurrentItem(context);
        markItemSuccess(currentItem, mergeCommitSha);
        context.setConflicts(new ArrayList<>());
        context.setCurrentChangeId(null);
        updateContext(log, context, "冲突已解决，继续代码合并", PipelineRunLogStatusEnum.RUNNING.getStatus());
        createEventLog(log, "继续代码合并", userId);
        continueMergeItems(run, log, context, userId);
    }

    @Override
    public void retryCurrentCodeMergeChange(Long pipelineRunId, Long userId) {
        PipelineRunDO run = validatePipelineRunExists(pipelineRunId);
        PipelineRunLogDO log = validateCodeMergeLogExists(pipelineRunId);
        CodeMergeContext context = parseCodeMergeContext(log);
        if (!PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(log.getStatus())) {
            throw exception(PIPELINE_RUN_LOG_STATE_INVALID);
        }
        CodeMergeItemContext currentItem = findCurrentItem(context);
        gitWorkspaceService.abortMerge(context.getWorkspaceKey());
        String commitSha = gitWorkspaceService.resolveRemoteBranchCommit(context.getWorkspaceKey(), currentItem.getBranchName());
        currentItem.setCommitSha(commitSha);
        currentItem.setStatus(CodeMergeItemContext.STATUS_PENDING);
        currentItem.setErrorMessage(null);
        context.setConflicts(new ArrayList<>());
        updateContext(log, context, "已刷新当前变更分支，准备重试", PipelineRunLogStatusEnum.RUNNING.getStatus());
        createEventLog(log, "刷新并重试当前变更：" + currentItem.getBranchName(), userId);
        continueMergeItems(run, log, context, userId);
    }

    @Override
    public void cancelRun(Long pipelineRunId, Long userId) {
        PipelineRunDO run = validatePipelineRunExists(pipelineRunId);
        PipelineRunLogDO log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeType(
                pipelineRunId, PipelineNodeTypeEnum.CODE_MERGE.getType());
        if (log != null) {
            CodeMergeContext context = parseCodeMergeContext(log);
            if (StrUtil.isNotBlank(context.getWorkspaceKey())) {
                gitWorkspaceService.abortMerge(context.getWorkspaceKey());
                gitWorkspaceService.cleanup(context.getWorkspaceKey());
            }
            log.setStatus(PipelineRunLogStatusEnum.CANCELED.getStatus());
            log.setFinishedAt(LocalDateTime.now());
            log.setSummary("流水线已取消");
            pipelineRunLogMapper.updateById(log);
            createEventLog(log, "取消流水线", userId);
        }
        run.setRunStatus(PipelineRunStatusEnum.CANCELED.getStatus());
        run.setFinishedAt(LocalDateTime.now());
        pipelineRunMapper.updateById(run);
    }

    private void executeCodeMerge(PipelineRunDO run, PipelineRunLogDO log, List<Long> changeIds, Long userId) {
        ApplicationEnvDO applicationEnv = validateApplicationEnvExists(run.getApplicationEnvId());
        ApplicationDO application = validateApplicationExists(run.getAppId());
        EnvironmentDO environment = validateEnvironmentExists(applicationEnv.getEnvId());
        RepositoryProviderDO provider = validateCodeMergeRepositoryProvider(application.getRepositoryProviderId());
        List<ChangeDO> changes = orderChanges(changeMapper.selectListByIds(changeIds), changeIds);
        String deployBranch = buildDeployBranch(application, environment, run);

        GitWorkspacePrepareResult workspace = gitWorkspaceService.prepareWorkspace(run.getId(), application.getRepoUrl(),
                provider.getAccessToken(), application.getDefaultBranchName(), deployBranch);
        CodeMergeContext context = new CodeMergeContext();
        context.setBaseBranch(application.getDefaultBranchName());
        context.setBaseCommitSha(workspace.getBaseCommitSha());
        context.setDeployBranch(deployBranch);
        context.setWorkspaceKey(workspace.getWorkspaceKey());
        context.setItems(changes.stream().map(this::buildItemContext).collect(Collectors.toCollection(ArrayList::new)));
        updateContext(log, context, "代码合并工作区已准备", PipelineRunLogStatusEnum.RUNNING.getStatus());
        continueMergeItems(run, log, context, userId);
    }

    private void continueMergeItems(PipelineRunDO run, PipelineRunLogDO log, CodeMergeContext context, Long userId) {
        for (CodeMergeItemContext item : context.getItems()) {
            if (CodeMergeItemContext.STATUS_SUCCESS.equals(item.getStatus())) {
                continue;
            }
            context.setCurrentChangeId(item.getChangeId());
            item.setStatus(CodeMergeItemContext.STATUS_RUNNING);
            item.setStartedAt(LocalDateTime.now());
            updateContext(log, context, "正在合并：" + item.getBranchName(), PipelineRunLogStatusEnum.RUNNING.getStatus());
            upsertStepLog(log, item, PipelineRunLogStatusEnum.RUNNING.getStatus(), "正在合并：" + item.getBranchName());
            updateChangeEnvMergeStatus(item.getChangeId(), run.getApplicationEnvId(), MergeStatusEnum.PENDING.getStatus(), null);
            if (StrUtil.isBlank(item.getCommitSha())) {
                item.setCommitSha(gitWorkspaceService.resolveRemoteBranchCommit(context.getWorkspaceKey(), item.getBranchName()));
            }
            GitMergeResult mergeResult = gitWorkspaceService.merge(context.getWorkspaceKey(), item.getCommitSha(),
                    "Merge change " + item.getChangeKey() + " into " + context.getDeployBranch());
            if (GitMergeResult.STATUS_SUCCESS.equals(mergeResult.getStatus())) {
                markItemSuccess(item, mergeResult.getMergeCommitSha());
                updateChangeEnvMergeStatus(item.getChangeId(), run.getApplicationEnvId(),
                        MergeStatusEnum.SUCCESS.getStatus(), null);
                updateContext(log, context, "合并成功：" + item.getBranchName(), PipelineRunLogStatusEnum.RUNNING.getStatus());
                upsertStepLog(log, item, PipelineRunLogStatusEnum.SUCCESS.getStatus(), "合并成功：" + item.getBranchName());
                continue;
            }
            item.setStatus(CodeMergeItemContext.STATUS_CONFLICTING);
            item.setErrorMessage(StrUtil.subPre(mergeResult.getOutput(), 1000));
            context.setConflicts(mergeResult.getConflicts().stream().map(this::buildConflictContext)
                    .collect(Collectors.toCollection(ArrayList::new)));
            updateChangeEnvMergeStatus(item.getChangeId(), run.getApplicationEnvId(), MergeStatusEnum.CONFLICT.getStatus(),
                    "代码合并冲突");
            updateContext(log, context, "代码合并冲突：" + item.getBranchName(),
                    PipelineRunLogStatusEnum.WAITING_INPUT.getStatus());
            upsertStepLog(log, item, PipelineRunLogStatusEnum.WAITING_INPUT.getStatus(), "代码合并冲突：" + item.getBranchName());
            return;
        }
        finishCodeMerge(run, log, context);
    }

    private void finishCodeMerge(PipelineRunDO run, PipelineRunLogDO log, CodeMergeContext context) {
        gitWorkspaceService.pushDeployBranch(context.getWorkspaceKey(), context.getDeployBranch());
        CodeMergeResultContext result = new CodeMergeResultContext();
        result.setDeployBranch(context.getDeployBranch());
        result.setDeployCommitSha(context.getItems().get(context.getItems().size() - 1).getMergeCommitSha());
        result.setMergedChangeIds(context.getItems().stream().map(CodeMergeItemContext::getChangeId).toList());
        log.setStatus(PipelineRunLogStatusEnum.SUCCESS.getStatus());
        log.setFinishedAt(LocalDateTime.now());
        log.setSummary("代码合并完成：" + context.getDeployBranch());
        log.setContextJson(JsonUtils.toJsonString(context));
        log.setResultJson(JsonUtils.toJsonString(result));
        pipelineRunLogMapper.updateById(log);

        run.setRunStatus(PipelineRunStatusEnum.SUCCESS.getStatus());
        run.setFinishedAt(LocalDateTime.now());
        pipelineRunMapper.updateById(run);
        gitWorkspaceService.cleanup(context.getWorkspaceKey());
    }

    private PipelineRunLogDO createNodeLog(Long pipelineRunId, String status, String summary) {
        PipelineRunLogDO log = new PipelineRunLogDO();
        log.setPipelineRunId(pipelineRunId);
        log.setNodeId(CODE_MERGE_NODE_ID);
        log.setNodeType(PipelineNodeTypeEnum.CODE_MERGE.getType());
        log.setNodeName(CODE_MERGE_NODE_NAME);
        log.setLogLevel(PipelineRunLogLevelEnum.NODE.getLevel());
        log.setStatus(status);
        log.setSort(10);
        log.setStartedAt(LocalDateTime.now());
        log.setSummary(summary);
        pipelineRunLogMapper.insert(log);
        return log;
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

    private void upsertStepLog(PipelineRunLogDO parent, CodeMergeItemContext item, String status, String summary) {
        String nodeId = parent.getNodeId() + ".change." + item.getChangeId();
        PipelineRunLogDO log = pipelineRunLogMapper.selectListByParentId(parent.getId()).stream()
                .filter(step -> nodeId.equals(step.getNodeId()))
                .findFirst()
                .orElse(null);
        if (log == null) {
            log = new PipelineRunLogDO();
            log.setPipelineRunId(parent.getPipelineRunId());
            log.setParentId(parent.getId());
            log.setNodeId(nodeId);
            log.setNodeType(parent.getNodeType());
            log.setNodeName(item.getBranchName());
            log.setLogLevel(PipelineRunLogLevelEnum.STEP.getLevel());
            log.setStatus(status);
            log.setSummary(summary);
            log.setContextJson(JsonUtils.toJsonString(item));
            log.setSort(100 + item.getChangeId().intValue());
            log.setStartedAt(LocalDateTime.now());
            pipelineRunLogMapper.insert(log);
        }
        log.setStatus(status);
        log.setSummary(summary);
        log.setContextJson(JsonUtils.toJsonString(item));
        if (PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(status)
                || PipelineRunLogStatusEnum.FAILED.getStatus().equals(status)
                || PipelineRunLogStatusEnum.CANCELED.getStatus().equals(status)) {
            log.setFinishedAt(LocalDateTime.now());
        }
        pipelineRunLogMapper.updateById(log);
    }

    private void updateContext(PipelineRunLogDO log, CodeMergeContext context, String summary, String status) {
        log.setContextJson(JsonUtils.toJsonString(context));
        log.setSummary(summary);
        log.setStatus(status);
        pipelineRunLogMapper.updateById(log);
    }

    private void markItemSuccess(CodeMergeItemContext item, String mergeCommitSha) {
        item.setStatus(CodeMergeItemContext.STATUS_SUCCESS);
        item.setMergeCommitSha(mergeCommitSha);
        item.setFinishedAt(LocalDateTime.now());
        item.setErrorMessage(null);
    }

    private CodeMergeItemContext buildItemContext(ChangeDO change) {
        CodeMergeItemContext item = new CodeMergeItemContext();
        item.setChangeId(change.getId());
        item.setChangeKey(change.getChangeKey());
        item.setBranchName(change.getBranchName());
        item.setCommitSha(change.getLatestCommitSha());
        item.setStatus(CodeMergeItemContext.STATUS_PENDING);
        return item;
    }

    private CodeMergeConflictContext buildConflictContext(GitConflictDescriptor descriptor) {
        CodeMergeConflictContext conflict = new CodeMergeConflictContext();
        conflict.setFilePath(descriptor.getFilePath());
        conflict.setConflictType(descriptor.getConflictType());
        conflict.setStatus(descriptor.getUnsupportedReason() == null
                ? CodeMergeConflictContext.STATUS_UNRESOLVED : CodeMergeConflictContext.STATUS_UNSUPPORTED);
        conflict.setBaseBlobSha(descriptor.getBaseBlobSha());
        conflict.setOursBlobSha(descriptor.getOursBlobSha());
        conflict.setTheirsBlobSha(descriptor.getTheirsBlobSha());
        conflict.setText(descriptor.getText());
        conflict.setContentSize(descriptor.getContentSize());
        conflict.setLineCount(descriptor.getLineCount());
        conflict.setCharset(descriptor.getCharset());
        conflict.setUnsupportedReason(descriptor.getUnsupportedReason());
        return conflict;
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
        respVO.setNodeId(log.getNodeId());
        respVO.setNodeType(log.getNodeType());
        respVO.setNodeName(log.getNodeName());
        respVO.setLogLevel(log.getLogLevel());
        respVO.setStatus(log.getStatus());
        respVO.setSort(log.getSort());
        respVO.setStartedAt(log.getStartedAt());
        respVO.setFinishedAt(log.getFinishedAt());
        respVO.setSummary(log.getSummary());
        respVO.setContext(JsonUtils.parseMap(log.getContextJson()));
        respVO.setResult(JsonUtils.parseMap(log.getResultJson()));
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

    private CodeMergeItemContext findCurrentItem(CodeMergeContext context) {
        return context.getItems().stream()
                .filter(item -> Objects.equals(item.getChangeId(), context.getCurrentChangeId()))
                .findFirst()
                .orElseThrow(() -> exception(PIPELINE_RUN_LOG_STATE_INVALID));
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
                pipelineRunId, PipelineNodeTypeEnum.CODE_MERGE.getType());
        if (log == null) {
            throw exception(PIPELINE_RUN_LOG_NOT_EXISTS);
        }
        return log;
    }

    private ApplicationDO validateApplicationExists(Long id) {
        ApplicationDO application = applicationMapper.selectById(id);
        if (application == null) {
            throw exception(APPLICATION_NOT_EXISTS);
        }
        return application;
    }

    private ApplicationEnvDO validateApplicationEnvExists(Long id) {
        ApplicationEnvDO applicationEnv = applicationEnvMapper.selectById(id);
        if (applicationEnv == null) {
            throw exception(APPLICATION_ENV_NOT_EXISTS);
        }
        return applicationEnv;
    }

    private EnvironmentDO validateEnvironmentExists(Long id) {
        EnvironmentDO environment = environmentMapper.selectById(id);
        if (environment == null) {
            throw exception(ENVIRONMENT_NOT_EXISTS);
        }
        return environment;
    }

    private RepositoryProviderDO validateCodeMergeRepositoryProvider(Long id) {
        RepositoryProviderDO provider = repositoryProviderService.getRepositoryProvider(id);
        if (provider == null) {
            throw exception(REPOSITORY_PROVIDER_NOT_EXISTS);
        }
        if (!RepositoryProviderTypeEnum.GITLAB.getProviderType().equals(provider.getProviderType())
                || !RepositoryProviderAuthTypeEnum.ACCESS_TOKEN.getAuthType().equals(provider.getAuthType())) {
            throw exception(PIPELINE_CODE_MERGE_REPOSITORY_AUTH_NOT_SUPPORTED);
        }
        return provider;
    }

    private void validateNoOtherActiveRun(PipelineRunDO run) {
        List<PipelineRunDO> activeRuns = pipelineRunMapper.selectListByApplicationEnvIdAndStatuses(
                run.getApplicationEnvId(), List.of(PipelineRunStatusEnum.QUEUED.getStatus(),
                        PipelineRunStatusEnum.RUNNING.getStatus()));
        boolean hasOther = activeRuns.stream().anyMatch(activeRun -> !Objects.equals(activeRun.getId(), run.getId()));
        if (hasOther) {
            throw exception(PIPELINE_RUN_ACTIVE_EXISTS);
        }
    }

    private List<ChangeDO> orderChanges(List<ChangeDO> changes, List<Long> changeIds) {
        Map<Long, ChangeDO> changeMap = changes.stream()
                .collect(Collectors.toMap(ChangeDO::getId, Function.identity(), (first, second) -> first,
                        LinkedHashMap::new));
        return changeIds.stream().map(changeMap::get).filter(Objects::nonNull).toList();
    }

    private String buildDeployBranch(ApplicationDO application, EnvironmentDO environment, PipelineRunDO run) {
        return "deploy/" + sanitizeRefPart(application.getAppKey()) + "/"
                + sanitizeRefPart(environment.getEnvKey()) + "/" + run.getId();
    }

    private String sanitizeRefPart(String value) {
        return StrUtil.blankToDefault(value, "unknown")
                .replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("-+", "-");
    }

    private void updateChangeEnvMergeStatus(Long changeId, Long applicationEnvId, Integer mergeStatus, String errorMessage) {
        ChangeEnvDO changeEnv = changeEnvMapper.selectByChangeIdAndApplicationEnvId(changeId, applicationEnvId);
        if (changeEnv == null) {
            return;
        }
        changeEnv.setLastMergeStatus(mergeStatus);
        changeEnv.setLastErrorMessage(errorMessage);
        changeEnvMapper.updateById(changeEnv);
    }

    private void failRunAndLog(PipelineRunDO run, PipelineRunLogDO log, String errorMessage) {
        String message = StrUtil.subPre(errorMessage, 1000);
        log.setStatus(PipelineRunLogStatusEnum.FAILED.getStatus());
        log.setFinishedAt(LocalDateTime.now());
        log.setSummary("代码合并失败");
        log.setErrorMessage(message);
        pipelineRunLogMapper.updateById(log);
        run.setRunStatus(PipelineRunStatusEnum.FAILED.getStatus());
        run.setFinishedAt(LocalDateTime.now());
        run.setErrorMessage(message);
        pipelineRunMapper.updateById(run);
    }

    private String sanitizeGitOutput(String output, String token) {
        String result = StrUtil.subPre(StrUtil.blankToDefault(output, ""), 1000);
        if (StrUtil.isNotBlank(token)) {
            result = result.replace(token, "***");
        }
        return result.replaceAll("oauth2:[^@\\s]+@", "oauth2:***@");
    }

}
