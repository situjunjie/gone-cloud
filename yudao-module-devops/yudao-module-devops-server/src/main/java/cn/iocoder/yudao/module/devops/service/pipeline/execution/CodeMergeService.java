package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
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
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogLevelEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderAuthTypeEnum;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderTypeEnum;
import cn.iocoder.yudao.module.devops.framework.git.GitCommandException;
import cn.iocoder.yudao.module.devops.framework.git.GitConflictDescriptor;
import cn.iocoder.yudao.module.devops.framework.git.GitFileResolution;
import cn.iocoder.yudao.module.devops.framework.git.GitMergeResult;
import cn.iocoder.yudao.module.devops.framework.git.GitWorkspacePrepareResult;
import cn.iocoder.yudao.module.devops.framework.git.GitWorkspaceService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.CodeMergeConflictContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.CodeMergeContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.CodeMergeItemContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.CodeMergeResultContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.PipelineRunChangeSnapshotContext;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.*;

/**
 * 代码合并执行服务。
 *
 * <p>只负责 CODE_MERGE 节点状态机：准备工作区、逐项合并、冲突挂起、推送部署分支和写回 run。
 * 引擎重入由调用方处理，避免 engine → handler → service → engine 的循环依赖。
 */
@Service
public class CodeMergeService {

    public static final String CODE_MERGE_NODE_ID = "builtin.code_merge";
    public static final String CODE_MERGE_NODE_NAME = "代码合并";

    private static final String RELEASE_BRANCH_PREFIX = "release/";
    private static final String LEGACY_DEPLOY_BRANCH_PREFIX = "deploy/";

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

    public PipelineRunLogDO getCodeMergeLog(Long pipelineRunId, String nodeId) {
        PipelineRunLogDO log = StrUtil.isBlank(nodeId) ? null
                : pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(pipelineRunId, nodeId);
        if (log != null) {
            return log;
        }
        log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeType(
                pipelineRunId, PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE);
        return log == null ? pipelineRunLogMapper.selectByPipelineRunIdAndNodeType(
                pipelineRunId, PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE_LEGACY) : log;
    }

    public PipelineRunLogDO getOrCreateCodeMergeLog(PipelineRunDO run, String nodeId, String nodeName) {
        PipelineRunLogDO log = getCodeMergeLog(run.getId(), nodeId);
        if (log != null) {
            return log;
        }
        log = new PipelineRunLogDO();
        log.setPipelineRunId(run.getId());
        log.setTenantId(run.getTenantId());
        log.setNodeId(StrUtil.blankToDefault(nodeId, CODE_MERGE_NODE_ID));
        log.setNodeType(PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE);
        log.setNodeName(StrUtil.blankToDefault(nodeName, CODE_MERGE_NODE_NAME));
        log.setLogLevel(PipelineRunLogLevelEnum.NODE.getLevel());
        log.setStatus(PipelineRunLogStatusEnum.RUNNING.getStatus());
        log.setSort(10);
        log.setAttempt(1);
        log.setRuntimeType("PLATFORM");
        log.setStartedAt(LocalDateTime.now());
        log.setSummary("开始代码合并");
        pipelineRunLogMapper.insert(log);
        return log;
    }

    public CodeMergeExecutionStatus executeCodeMergeNode(PipelineRunDO run, String nodeId, String nodeName, Long userId) {
        PipelineRunLogDO log = getOrCreateCodeMergeLog(run, nodeId, nodeName);
        if (PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(log.getStatus())) {
            return CodeMergeExecutionStatus.SUCCESS;
        }
        if (PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(log.getStatus())) {
            return CodeMergeExecutionStatus.SUSPEND;
        }
        if (PipelineRunLogStatusEnum.FAILED.getStatus().equals(log.getStatus())
                || PipelineRunLogStatusEnum.CANCELED.getStatus().equals(log.getStatus())) {
            return CodeMergeExecutionStatus.FAIL;
        }
        if (StrUtil.isNotBlank(log.getContextJson())) {
            return continueMergeItems(run, log, parseCodeMergeContext(log), userId);
        }
        return startCodeMerge(run, log, parseChangeIds(run), userId);
    }

    public CodeMergeExecutionStatus executeCodeMergeStep(PipelineRunDO run, PipelineRunLogDO log,
                                                         List<String> branches, boolean branchesFromSubmit,
                                                         String baseBranch, String targetBranch,
                                                         boolean pushOnSuccess, Long userId) {
        if (PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(log.getStatus())) {
            return CodeMergeExecutionStatus.SUCCESS;
        }
        if (PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(log.getStatus())) {
            return CodeMergeExecutionStatus.SUSPEND;
        }
        if (PipelineRunLogStatusEnum.FAILED.getStatus().equals(log.getStatus())
                || PipelineRunLogStatusEnum.CANCELED.getStatus().equals(log.getStatus())) {
            return CodeMergeExecutionStatus.FAIL;
        }
        if (StrUtil.isNotBlank(log.getContextJson())) {
            return continueMergeItems(run, log, parseCodeMergeContext(log), userId);
        }
        validateNoOtherActiveRun(run);
        try {
            startCodeMergeStep(run, log, branches, branchesFromSubmit, baseBranch, targetBranch, pushOnSuccess, userId);
            return statusFromLog(log);
        } catch (GitCommandException ex) {
            failRunAndLog(run, log, sanitizeGitOutput(ex.getMessage() + ": " + ex.getOutput(), null));
            return CodeMergeExecutionStatus.FAIL;
        } catch (Exception ex) {
            failRunAndLog(run, log, StrUtil.subPre(ex.getMessage(), 1000));
            return CodeMergeExecutionStatus.FAIL;
        }
    }

    public CodeMergeExecutionStatus startCodeMerge(PipelineRunDO run, PipelineRunLogDO log,
                                                   List<Long> changeIds, Long userId) {
        validateNoOtherActiveRun(run);
        try {
            executeCodeMerge(run, log, changeIds, userId);
            return statusFromLog(log);
        } catch (GitCommandException ex) {
            failRunAndLog(run, log, sanitizeGitOutput(ex.getMessage() + ": " + ex.getOutput(), null));
            return CodeMergeExecutionStatus.FAIL;
        } catch (Exception ex) {
            failRunAndLog(run, log, StrUtil.subPre(ex.getMessage(), 1000));
            return CodeMergeExecutionStatus.FAIL;
        }
    }

    public CodeMergeExecutionStatus continueResolvedConflicts(PipelineRunDO run, PipelineRunLogDO log,
                                                              CodeMergeContext context, List<GitFileResolution> resolutions,
                                                              Long userId) {
        try {
            String mergeCommitSha = gitWorkspaceService.continueMerge(context.getWorkspaceKey(), resolutions,
                    "Resolve conflicts for change " + context.getCurrentChangeId());
            CodeMergeItemContext currentItem = findCurrentItem(context);
            markItemSuccess(currentItem, mergeCommitSha);
            updateChangeEnvMergeStatus(currentItem.getChangeId(), run.getApplicationEnvId(),
                    MergeStatusEnum.SUCCESS.getStatus(), null);
            upsertStepLog(log, currentItem, PipelineRunLogStatusEnum.SUCCESS.getStatus(),
                    "冲突已解决并合并成功：" + currentItem.getBranchName());
            context.setConflicts(new ArrayList<>());
            context.setCurrentChangeId(null);
            context.setCurrentBranchName(null);
            updateContext(log, context, "冲突已解决，继续代码合并", PipelineRunLogStatusEnum.RUNNING.getStatus());
            createEventLog(log, "继续代码合并", userId);
            return continueMergeItems(run, log, context, userId);
        } catch (GitCommandException ex) {
            failRunAndLog(run, log, sanitizeGitOutput(ex.getMessage() + ": " + ex.getOutput(), null));
            return CodeMergeExecutionStatus.FAIL;
        } catch (Exception ex) {
            failRunAndLog(run, log, StrUtil.subPre(ex.getMessage(), 1000));
            return CodeMergeExecutionStatus.FAIL;
        }
    }

    public CodeMergeExecutionStatus retryCurrentChange(PipelineRunDO run, PipelineRunLogDO log,
                                                       CodeMergeContext context, Long userId) {
        try {
            CodeMergeItemContext currentItem = findCurrentItem(context);
            gitWorkspaceService.abortMerge(context.getWorkspaceKey());
            String commitSha = gitWorkspaceService.resolveRemoteBranchCommit(context.getWorkspaceKey(),
                    currentItem.getBranchName());
            currentItem.setCommitSha(commitSha);
            currentItem.setStatus(CodeMergeItemContext.STATUS_PENDING);
            currentItem.setErrorMessage(null);
            context.setConflicts(new ArrayList<>());
            updateContext(log, context, "已刷新当前变更分支，准备重试", PipelineRunLogStatusEnum.RUNNING.getStatus());
            createEventLog(log, "刷新并重试当前变更：" + currentItem.getBranchName(), userId);
            return continueMergeItems(run, log, context, userId);
        } catch (GitCommandException ex) {
            failRunAndLog(run, log, sanitizeGitOutput(ex.getMessage() + ": " + ex.getOutput(), null));
            return CodeMergeExecutionStatus.FAIL;
        } catch (Exception ex) {
            failRunAndLog(run, log, StrUtil.subPre(ex.getMessage(), 1000));
            return CodeMergeExecutionStatus.FAIL;
        }
    }

    public void cancelCodeMergeIfActive(PipelineRunDO run, Long userId) {
        PipelineRunLogDO log = getCodeMergeLog(run.getId(), CODE_MERGE_NODE_ID);
        if (log == null || isTerminalLogStatus(log.getStatus())) {
            return;
        }
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

    private void executeCodeMerge(PipelineRunDO run, PipelineRunLogDO log, List<Long> changeIds, Long userId) {
        ApplicationEnvDO applicationEnv = validateApplicationEnvExists(run.getApplicationEnvId());
        ApplicationDO application = validateApplicationExists(run.getAppId());
        EnvironmentDO environment = validateEnvironmentExists(applicationEnv.getEnvId());
        RepositoryProviderDO provider = validateCodeMergeRepositoryProvider(application.getRepositoryProviderId());
        List<ChangeDO> selectedChanges = CollUtil.isEmpty(changeIds)
                ? List.of() : changeMapper.selectListByIds(changeIds);
        List<ChangeDO> changes = orderChanges(selectedChanges, changeIds);
        String deployBranch = resolveDeployBranch(application, environment, run);

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

    private void startCodeMergeStep(PipelineRunDO run, PipelineRunLogDO log, List<String> branches,
                                    boolean branchesFromSubmit, String baseBranch, String targetBranch,
                                    boolean pushOnSuccess, Long userId) {
        ApplicationDO application = validateApplicationExists(run.getAppId());
        RepositoryProviderDO provider = validateCodeMergeRepositoryProvider(application.getRepositoryProviderId());
        String resolvedBaseBranch = firstNotBlank(baseBranch, application.getDefaultBranchName());
        String resolvedTargetBranch = firstNotBlank(targetBranch, run.getBranchName());
        if (StrUtil.isBlank(resolvedBaseBranch)) {
            throw new IllegalStateException("baseBranch is required");
        }
        if (StrUtil.isBlank(resolvedTargetBranch)) {
            throw new IllegalStateException("targetBranch is required");
        }
        List<CodeMergeItemContext> items = CollUtil.isNotEmpty(branches)
                ? buildBranchItems(application, branches)
                : branchesFromSubmit ? buildSubmitItems(run) : List.of();
        GitWorkspacePrepareResult workspace = gitWorkspaceService.prepareWorkspace(run.getId(), application.getRepoUrl(),
                provider.getAccessToken(), resolvedBaseBranch, resolvedTargetBranch);
        CodeMergeContext context = new CodeMergeContext();
        context.setBaseBranch(resolvedBaseBranch);
        context.setBaseCommitSha(workspace.getBaseCommitSha());
        context.setDeployBranch(resolvedTargetBranch);
        context.setWorkspaceKey(workspace.getWorkspaceKey());
        context.setPushOnSuccess(pushOnSuccess);
        context.setItems(new ArrayList<>(items));
        updateContext(log, context, "代码合并工作区已准备", PipelineRunLogStatusEnum.RUNNING.getStatus());
        continueMergeItems(run, log, context, userId);
    }

    private CodeMergeExecutionStatus continueMergeItems(PipelineRunDO run, PipelineRunLogDO log,
                                                        CodeMergeContext context, Long userId) {
        for (CodeMergeItemContext item : context.getItems()) {
            if (CodeMergeItemContext.STATUS_SUCCESS.equals(item.getStatus())) {
                continue;
            }
            context.setCurrentChangeId(item.getChangeId());
            context.setCurrentBranchName(item.getBranchName());
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
            return CodeMergeExecutionStatus.SUSPEND;
        }
        finishCodeMerge(run, log, context);
        return CodeMergeExecutionStatus.SUCCESS;
    }

    private void finishCodeMerge(PipelineRunDO run, PipelineRunLogDO log, CodeMergeContext context) {
        if (!Boolean.FALSE.equals(context.getPushOnSuccess())) {
            gitWorkspaceService.pushDeployBranch(context.getWorkspaceKey(), context.getDeployBranch());
        }
        CodeMergeResultContext result = new CodeMergeResultContext();
        result.setDeployBranch(context.getDeployBranch());
        result.setDeployCommitSha(CollUtil.isEmpty(context.getItems()) ? context.getBaseCommitSha()
                : context.getItems().get(context.getItems().size() - 1).getMergeCommitSha());
        result.setMergedChangeIds(context.getItems().stream().map(CodeMergeItemContext::getChangeId)
                .filter(Objects::nonNull).toList());
        log.setStatus(PipelineRunLogStatusEnum.SUCCESS.getStatus());
        log.setFinishedAt(LocalDateTime.now());
        log.setSummary("代码合并完成：" + context.getDeployBranch());
        log.setContextJson(JsonUtils.toJsonString(context));
        log.setResultJson(JsonUtils.toJsonString(result));
        pipelineRunLogMapper.updateById(log);
        run.setBranchName(result.getDeployBranch());
        run.setCommitSha(result.getDeployCommitSha());
        run.setRunStatus(PipelineRunStatusEnum.RUNNING.getStatus());
        run.setFinishedAt(null);
        run.setErrorMessage(null);
        pipelineRunMapper.updateById(run);
        gitWorkspaceService.cleanup(context.getWorkspaceKey());
    }

    private PipelineRunLogDO createNodeLog(Long pipelineRunId, String status, String summary) {
        PipelineRunLogDO log = new PipelineRunLogDO();
        log.setPipelineRunId(pipelineRunId);
        log.setNodeId(CODE_MERGE_NODE_ID);
        log.setNodeType(PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE);
        log.setNodeName(CODE_MERGE_NODE_NAME);
        log.setLogLevel(PipelineRunLogLevelEnum.NODE.getLevel());
        log.setStatus(status);
        log.setSort(10);
        log.setAttempt(1);
        log.setRuntimeType("PLATFORM");
        log.setStartedAt(LocalDateTime.now());
        log.setSummary(summary);
        pipelineRunLogMapper.insert(log);
        return log;
    }

    private void createEventLog(PipelineRunLogDO parent, String summary, Long userId) {
        PipelineRunLogDO log = new PipelineRunLogDO();
        log.setPipelineRunId(parent.getPipelineRunId());
        log.setTenantId(parent.getTenantId());
        log.setParentId(parent.getId());
        log.setNodeId(parent.getNodeId() + ".event");
        log.setNodeType(parent.getNodeType());
        log.setNodeName("事件");
        log.setLogLevel(PipelineRunLogLevelEnum.EVENT.getLevel());
        log.setStatus(PipelineRunLogStatusEnum.SUCCESS.getStatus());
        log.setSort(1000);
        log.setAttempt(parent.getAttempt());
        log.setRuntimeType(parent.getRuntimeType());
        log.setStageId(parent.getStageId());
        log.setStageName(parent.getStageName());
        log.setJobId(parent.getJobId());
        log.setJobName(parent.getJobName());
        log.setStartedAt(LocalDateTime.now());
        log.setFinishedAt(LocalDateTime.now());
        log.setSummary(summary);
        log.setContextJson(JsonUtils.toJsonString(Map.of("userId", userId)));
        pipelineRunLogMapper.insert(log);
    }

    private void upsertStepLog(PipelineRunLogDO parent, CodeMergeItemContext item, String status, String summary) {
        String itemKey = item.getChangeId() == null ? "branch." + sanitizeRefPart(item.getBranchName())
                : "change." + item.getChangeId();
        String nodeId = parent.getNodeId() + "." + itemKey;
        PipelineRunLogDO log = pipelineRunLogMapper.selectListByParentId(parent.getId()).stream()
                .filter(step -> nodeId.equals(step.getNodeId()))
                .findFirst()
                .orElse(null);
        if (log == null) {
            log = new PipelineRunLogDO();
            log.setPipelineRunId(parent.getPipelineRunId());
            log.setTenantId(parent.getTenantId());
            log.setParentId(parent.getId());
            log.setNodeId(nodeId);
            log.setNodeType(parent.getNodeType());
            log.setNodeName(item.getBranchName());
            log.setLogLevel(PipelineRunLogLevelEnum.STEP.getLevel());
            log.setStatus(status);
            log.setSummary(summary);
            log.setContextJson(JsonUtils.toJsonString(item));
            log.setSort(100 + Math.abs(itemKey.hashCode() % 100000));
            log.setAttempt(parent.getAttempt());
            log.setRuntimeType(parent.getRuntimeType());
            log.setStageId(parent.getStageId());
            log.setStageName(parent.getStageName());
            log.setJobId(parent.getJobId());
            log.setJobName(parent.getJobName());
            log.setStartedAt(LocalDateTime.now());
            pipelineRunLogMapper.insert(log);
        }
        log.setStatus(status);
        log.setSummary(summary);
        log.setContextJson(JsonUtils.toJsonString(item));
        if (isTerminalLogStatus(status)) {
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

    private CodeMergeItemContext buildBranchItemContext(ApplicationDO application, String branchName) {
        ChangeDO change = changeMapper.selectByAppIdAndBranchName(application.getId(), branchName);
        if (change != null) {
            return buildItemContext(change);
        }
        CodeMergeItemContext item = new CodeMergeItemContext();
        item.setChangeKey(branchName);
        item.setBranchName(branchName);
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

    private CodeMergeItemContext findCurrentItem(CodeMergeContext context) {
        return context.getItems().stream()
                .filter(item -> Objects.equals(item.getChangeId(), context.getCurrentChangeId())
                        || Objects.equals(item.getBranchName(), context.getCurrentBranchName()))
                .findFirst()
                .orElseThrow(() -> exception(PIPELINE_RUN_LOG_STATE_INVALID));
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

    private List<Long> parseChangeIds(PipelineRunDO run) {
        List<PipelineRunChangeSnapshotContext> snapshots = JsonUtils.parseArray(run.getChangeSnapshotJson(),
                PipelineRunChangeSnapshotContext.class);
        if (CollUtil.isNotEmpty(snapshots)) {
            return snapshots.stream().map(PipelineRunChangeSnapshotContext::getChangeId)
                    .filter(Objects::nonNull).toList();
        }
        return run.getChangeId() == null ? List.of() : List.of(run.getChangeId());
    }

    private List<CodeMergeItemContext> buildSubmitItems(PipelineRunDO run) {
        List<Long> changeIds = parseChangeIds(run);
        if (CollUtil.isEmpty(changeIds)) {
            return List.of();
        }
        return orderChanges(changeMapper.selectListByIds(changeIds), changeIds).stream()
                .map(this::buildItemContext)
                .toList();
    }

    private List<CodeMergeItemContext> buildBranchItems(ApplicationDO application, List<String> branches) {
        if (CollUtil.isEmpty(branches)) {
            return List.of();
        }
        return branches.stream()
                .filter(StrUtil::isNotBlank)
                .map(branch -> buildBranchItemContext(application, branch))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String buildLegacyDeployBranch(ApplicationDO application, EnvironmentDO environment, PipelineRunDO run) {
        return "deploy/" + sanitizeRefPart(application.getAppKey()) + "/"
                + sanitizeRefPart(environment.getEnvKey()) + "/" + run.getId();
    }

    private String resolveDeployBranch(ApplicationDO application, EnvironmentDO environment, PipelineRunDO run) {
        return isReleaseBranch(run.getBranchName()) ? run.getBranchName() : buildLegacyDeployBranch(application, environment, run);
    }

    private boolean isReleaseBranch(String branchName) {
        return StrUtil.startWith(branchName, RELEASE_BRANCH_PREFIX)
                || StrUtil.startWith(branchName, LEGACY_DEPLOY_BRANCH_PREFIX);
    }

    private String sanitizeRefPart(String value) {
        return StrUtil.blankToDefault(value, "unknown")
                .replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("-+", "-");
    }

    private void updateChangeEnvMergeStatus(Long changeId, Long applicationEnvId, Integer mergeStatus, String errorMessage) {
        if (changeId == null || applicationEnvId == null) {
            return;
        }
        ChangeEnvDO changeEnv = changeEnvMapper.selectByChangeIdAndApplicationEnvId(changeId, applicationEnvId);
        if (changeEnv == null) {
            return;
        }
        changeEnvMapper.updateMergeStatus(changeEnv.getId(), mergeStatus, errorMessage);
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

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private CodeMergeExecutionStatus statusFromLog(PipelineRunLogDO log) {
        if (PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(log.getStatus())) {
            return CodeMergeExecutionStatus.SUCCESS;
        }
        if (PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(log.getStatus())) {
            return CodeMergeExecutionStatus.SUSPEND;
        }
        return CodeMergeExecutionStatus.FAIL;
    }

    private boolean isTerminalLogStatus(String status) {
        return PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(status)
                || PipelineRunLogStatusEnum.FAILED.getStatus().equals(status)
                || PipelineRunLogStatusEnum.CANCELED.getStatus().equals(status);
    }

}
