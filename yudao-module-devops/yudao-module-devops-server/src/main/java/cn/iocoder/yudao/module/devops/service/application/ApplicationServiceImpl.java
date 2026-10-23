package cn.iocoder.yudao.module.devops.service.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.user.UserFavoriteApi;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseBranchRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseChangeSnapshotRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseCurrentRunRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseEnvDetailRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseEnvTabRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleasePipelineEdgeRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleasePipelineNodeRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleasePipelineRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseSubmitBranchReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseSubmitBranchRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseUploadImageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseUploadImageRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationEnvRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationEnvSaveReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationSaveReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationUpdateEnvsReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationEnvUpdateReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationRespVO;
import cn.iocoder.yudao.module.devops.convert.application.ApplicationConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.job.PipelineRunJobDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.variablegroup.VariableGroupDO;
import cn.iocoder.yudao.module.devops.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.environment.EnvironmentMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionVersionMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.job.PipelineRunJobMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.variablegroup.VariableGroupMapper;
import cn.iocoder.yudao.module.devops.enums.ApprovalStatusEnum;
import cn.iocoder.yudao.module.devops.enums.ChangeEnvMountStatusEnum;
import cn.iocoder.yudao.module.devops.enums.ChangeStatusEnum;
import cn.iocoder.yudao.module.devops.enums.MergeStatusEnum;
import cn.iocoder.yudao.module.devops.enums.VariableGroupScopeEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunJobStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineStatusEnum;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineSpecValidationService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionAsyncService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.PipelineApprovalContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.PipelineRunChangeSnapshotContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepActionKeyConstants;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.*;

/**
 * DevOps 应用 Service 实现类。
 */
@Slf4j
@Service
@Validated
public class ApplicationServiceImpl implements ApplicationService {

    private static final String JOB_NODE_TYPE = "JOB";
    private static final String DETAIL_TYPE_RUN_LOGS = "RUN_LOGS";
    private static final String DETAIL_TYPE_CODE_MERGE = "CODE_MERGE";
    private static final String DETAIL_TYPE_APPROVAL = "APPROVAL";
    private static final String DETAIL_TYPE_DEPLOYMENT_ORDER = "DEPLOYMENT_ORDER";
    private static final String NODE_STATUS_NOT_STARTED = "NOT_STARTED";
    private static final String NODE_STATUS_RUNNING = "RUNNING";
    private static final String NODE_STATUS_BLOCKED = "BLOCKED";
    private static final String NODE_STATUS_COMPLETED = "COMPLETED";
    private static final String ACTION_RESOLVE_CODE_CONFLICT = "RESOLVE_CODE_CONFLICT";
    private static final String ACTION_OPEN_APPROVAL_DETAIL = "OPEN_APPROVAL_DETAIL";
    private static final String ACTION_TARGET_ROUTE = "ROUTE";
    private static final String ACTION_TARGET_API = "API";
    private static final String RESULT_ACTIONS_KEY = "actions";
    private static final Set<String> CURRENT_RUN_RESULT_ALLOWED_KEYS = Set.of(
            "artifacts", "reports", "image", "archiveFormat", "compression", "outputFileName", "storagePath", "downloadUrl",
            "exitCode", "logTruncated", "logFileUrl", "logArchiveStatus",
            "deploymentOrderId", "deployStatus", "currentStage", "namespace", "workloadName", "containerName",
            "replicas");
    private static final Set<String> CURRENT_RUN_RESULT_BLOCKED_KEYS = Set.of(
            "logLines", "outputs", "context", "contextJson", "workspacePath", "workspace", "runtime",
            "env", "environment", "variables");
    private static final String DEPLOY_BRANCH_PREFIX = "release/";
    public static final String TRIGGER_TYPE_APPLICATION_RELEASE_TAB = "APPLICATION_RELEASE_TAB";
    public static final String TRIGGER_TYPE_APPLICATION_UPLOAD_IMAGE = "APPLICATION_UPLOAD_IMAGE";
    public static final String TRIGGER_MODE_UPLOAD_IMAGE = "UPLOAD_IMAGE";
    private static final DateTimeFormatter DEPLOY_BRANCH_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource
    private ApplicationMapper applicationMapper;
    @Resource
    private ApplicationEnvMapper applicationEnvMapper;
    @Resource
    private VariableGroupMapper variableGroupMapper;
    @Resource
    private EnvironmentMapper environmentMapper;
    @Resource
    private ChangeMapper changeMapper;
    @Resource
    private ChangeEnvMapper changeEnvMapper;
    @Resource
    private PipelineDefinitionMapper pipelineDefinitionMapper;
    @Resource
    private PipelineDefinitionVersionMapper pipelineDefinitionVersionMapper;
    @Resource
    private PipelineRunMapper pipelineRunMapper;
    @Resource
    private PipelineRunJobMapper pipelineRunJobMapper;
    @Resource
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Resource
    private PipelineSpecValidationService pipelineSpecValidationService;
    @Resource
    private RepositoryProviderService repositoryProviderService;
    @Resource
    private PipelineExecutionAsyncService pipelineExecutionAsyncService;
    @Resource
    private PipelineExecutionService pipelineExecutionService;
    @Resource
    private UserFavoriteApi userFavoriteApi;

    @Override
    public Long createApplication(ApplicationSaveReqVO createReqVO) {
        RepositoryProviderDO repositoryProvider = repositoryProviderService
                .validateRepositoryProviderExists(createReqVO.getRepositoryProviderId());
        validateApplicationUnique(null, createReqVO.getAppKey(),
                createReqVO.getRepositoryProviderId(), createReqVO.getRepoIdentifier());

        ApplicationDO application = ApplicationConvert.INSTANCE.convert(createReqVO);
        application.setRepoProviderType(repositoryProvider.getProviderType());
        applicationMapper.insert(application);
        return application.getId();
    }

    @Override
    public void updateApplication(ApplicationSaveReqVO updateReqVO) {
        validateApplicationExists(updateReqVO.getId());
        RepositoryProviderDO repositoryProvider = repositoryProviderService
                .validateRepositoryProviderExists(updateReqVO.getRepositoryProviderId());
        validateApplicationUnique(updateReqVO.getId(), updateReqVO.getAppKey(),
                updateReqVO.getRepositoryProviderId(), updateReqVO.getRepoIdentifier());

        ApplicationDO updateObj = ApplicationConvert.INSTANCE.convert(updateReqVO);
        updateObj.setRepoProviderType(repositoryProvider.getProviderType());
        applicationMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteApplication(Long id) {
        validateApplicationExists(id);
        if (changeMapper.selectCount(ChangeDO::getAppId, id) > 0) {
            throw exception(APPLICATION_DELETE_FAIL_CHANGE_EXISTS);
        }

        applicationEnvMapper.deleteByAppId(id);
        applicationMapper.deleteById(id);
    }

    @Override
    public ApplicationDO getApplication(Long id) {
        return applicationMapper.selectById(id);
    }

    @Override
    public PageResult<ApplicationDO> getApplicationPage(ApplicationPageReqVO pageReqVO, Long userId) {
        if (pageReqVO.getFavori() != null) {
            pageReqVO.setFavoriIds(userFavoriteApi.getUserFavoriteList("DEVOPS_APPLICATION").getCheckedData().stream()
                    .map(favorite -> favorite.getBizId()).toList());
        }
        // 「我参与的应用」：由当前登录用户过滤，调用方传入的 memberUserId 不可信
        if (Boolean.TRUE.equals(pageReqVO.getMine())) {
            pageReqVO.setMemberUserId(userId);
        } else {
            pageReqVO.setMemberUserId(null);
        }
        return applicationMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateApplicationEnvs(ApplicationUpdateEnvsReqVO updateReqVO) {
        ApplicationDO application = validateApplicationExists(updateReqVO.getAppId());
        validateApplicationEnvList(updateReqVO.getEnvs());

        if (CollUtil.isEmpty(updateReqVO.getEnvs())) {
            applicationEnvMapper.deleteByAppId(updateReqVO.getAppId());
            return;
        }

        Set<Long> requestedEnvIds = updateReqVO.getEnvs().stream()
                .map(ApplicationEnvSaveReqVO::getEnvId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<ApplicationEnvDO> activeEnvs = applicationEnvMapper.selectListByAppId(updateReqVO.getAppId());
        Set<Long> removedEnvIds = activeEnvs.stream().map(ApplicationEnvDO::getEnvId).collect(Collectors.toSet());
        removedEnvIds.removeAll(requestedEnvIds);
        applicationEnvMapper.deleteByAppIdAndEnvIds(updateReqVO.getAppId(), removedEnvIds);

        Map<Long, ApplicationEnvDO> existingEnvMap = applicationEnvMapper
                .selectListByTenantIdAndAppIdAndEnvIdsIncludingDeleted(application.getTenantId(),
                        updateReqVO.getAppId(), requestedEnvIds)
                .stream().collect(Collectors.toMap(ApplicationEnvDO::getEnvId, Function.identity()));
        List<ApplicationEnvDO> insertEnvs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (ApplicationEnvSaveReqVO envReqVO : updateReqVO.getEnvs()) {
            ApplicationEnvDO env = ApplicationConvert.INSTANCE.convert(envReqVO);
            env.setAppId(updateReqVO.getAppId());
            if (env.getVariableGroupId() != null) {
                VariableGroupDO variableGroup = variableGroupMapper.selectById(env.getVariableGroupId());
                if (variableGroup == null
                        || !VariableGroupScopeEnum.APPLICATION.getScope().equals(variableGroup.getScope())
                        || !updateReqVO.getAppId().equals(variableGroup.getAppId())) {
                    throw exception(VARIABLE_GROUP_SCOPE_INVALID);
                }
            }
            ApplicationEnvDO existingEnv = existingEnvMap.get(env.getEnvId());
            if (existingEnv == null) {
                insertEnvs.add(env);
                continue;
            }
            env.setId(existingEnv.getId());
            if (Boolean.TRUE.equals(existingEnv.getDeleted())) {
                applicationEnvMapper.restoreConfigById(env, application.getTenantId(), now);
            } else {
                applicationEnvMapper.updateConfigById(env, now);
            }
        }
        if (CollUtil.isNotEmpty(insertEnvs)) {
            applicationEnvMapper.insertBatch(insertEnvs);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateApplicationEnv(ApplicationEnvUpdateReqVO updateReqVO) {
        ApplicationEnvDO existingEnv = validateApplicationEnvExists(updateReqVO.getId());
        if (updateReqVO.getVariableGroupId() != null) {
            VariableGroupDO variableGroup = variableGroupMapper.selectById(updateReqVO.getVariableGroupId());
            if (variableGroup == null
                    || !VariableGroupScopeEnum.APPLICATION.getScope().equals(variableGroup.getScope())
                    || !existingEnv.getAppId().equals(variableGroup.getAppId())) {
                throw exception(VARIABLE_GROUP_SCOPE_INVALID);
            }
        }
        ApplicationEnvDO updateEnv = new ApplicationEnvDO();
        updateEnv.setId(existingEnv.getId());
        updateEnv.setAppId(existingEnv.getAppId());
        updateEnv.setEnvId(existingEnv.getEnvId());
        updateEnv.setName(updateReqVO.getName());
        updateEnv.setVariableGroupId(updateReqVO.getVariableGroupId());
        updateEnv.setDisplayOrder(updateReqVO.getDisplayOrder());
        updateEnv.setStatus(updateReqVO.getStatus());
        updateEnv.setRemark(updateReqVO.getRemark());
        applicationEnvMapper.updateConfigById(updateEnv, LocalDateTime.now());
    }

    @Override
    public List<ApplicationEnvRespVO> getApplicationEnvList(Long appId) {
        return ApplicationConvert.INSTANCE.convertEnvList(applicationEnvMapper.selectListByAppId(appId));
    }

    @Override
    public List<ApplicationReleaseEnvTabRespVO> getApplicationReleaseEnvTabs(Long appId) {
        validateApplicationExists(appId);
        List<ApplicationEnvDO> applicationEnvs = applicationEnvMapper.selectListByAppIdOrderByDisplayOrder(appId);
        if (CollUtil.isEmpty(applicationEnvs)) {
            return Collections.emptyList();
        }

        Map<Long, EnvironmentDO> environmentMap = environmentMapper.selectListByIds(applicationEnvs.stream()
                        .map(ApplicationEnvDO::getEnvId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(EnvironmentDO::getId, Function.identity()));
        Map<Long, PipelineDefinitionDO> pipelineDefinitionMap = pipelineDefinitionMapper
                .selectListByApplicationEnvIds(applicationEnvs.stream().map(ApplicationEnvDO::getId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(PipelineDefinitionDO::getApplicationEnvId, Function.identity()));

        return applicationEnvs.stream()
                .map(applicationEnv -> buildReleaseEnvTab(applicationEnv, environmentMap.get(applicationEnv.getEnvId()),
                        pipelineDefinitionMap.get(applicationEnv.getId())))
                .toList();
    }

    @Override
    public ApplicationReleaseEnvDetailRespVO getApplicationReleaseEnvDetail(Long applicationEnvId) {
        ApplicationEnvDO applicationEnv = validateApplicationEnvExists(applicationEnvId);
        EnvironmentDO environment = validateEnvironmentExists(applicationEnv.getEnvId());
        PipelineDefinitionDO pipelineDefinition = pipelineDefinitionMapper.selectByApplicationEnvId(applicationEnvId);

        ApplicationReleaseEnvDetailRespVO detail = new ApplicationReleaseEnvDetailRespVO();
        detail.setApplicationEnvName(StrUtil.blankToDefault(applicationEnv.getName(), environment.getEnvName()));
        detail.setEnv(buildReleaseEnvTab(applicationEnv, environment, pipelineDefinition));
        detail.setPipeline(buildReleasePipeline(pipelineDefinition));
        fillReleaseBranches(detail, applicationEnv);
        return detail;
    }

    @Override
    @Cacheable(value = RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN, key = "#applicationEnvId",
            unless = "#result == null")
    public ApplicationReleaseCurrentRunRespVO getApplicationReleaseCurrentRun(Long applicationEnvId) {
        ApplicationEnvDO applicationEnv = validateApplicationEnvExists(applicationEnvId);
        ApplicationDO application = validateApplicationExists(applicationEnv.getAppId());
        PipelineDefinitionDO pipelineDefinition = pipelineDefinitionMapper.selectByApplicationEnvId(applicationEnvId);
        ApplicationReleasePipelineRespVO pipeline = buildReleasePipeline(pipelineDefinition);
        PipelineRunDO run = getCurrentReleasePipelineRun(applicationEnvId);
        List<PipelineRunLogDO> runLogs = run == null ? Collections.emptyList()
                : pipelineRunLogMapper.selectListByPipelineRunId(run.getId());
        Map<String, PipelineRunJobDO> runJobMap = run == null ? Collections.emptyMap()
                : pipelineRunJobMapper.selectListByPipelineRunId(run.getId()).stream()
                .collect(Collectors.toMap(PipelineRunJobDO::getJobId, Function.identity(), (first, second) -> first,
                        LinkedHashMap::new));

        ApplicationReleaseCurrentRunRespVO respVO = new ApplicationReleaseCurrentRunRespVO();
        respVO.setApplicationEnvId(applicationEnvId);
        respVO.setHasRun(run != null);
        respVO.setPolling(run != null && isRunPolling(run));
        respVO.setChangeSnapshots(run == null ? Collections.emptyList() : parseRunChangeSnapshots(run));
        respVO.setMountedBranches(buildReleaseBranches(applicationEnv).getMountedBranches());
        fillBaseBranchStatus(respVO, application, run);
        if (run != null) {
            respVO.setPipelineRunId(run.getId());
            respVO.setRunStatus(run.getRunStatus());
            respVO.setTriggerType(run.getTriggerType());
            respVO.setTriggerUserId(run.getTriggerUserId());
            respVO.setTriggeredAt(run.getTriggeredAt());
            respVO.setStartedAt(run.getStartedAt());
            respVO.setFinishedAt(run.getFinishedAt());
            respVO.setErrorMessage(run.getErrorMessage());
        }
        respVO.setNodes(buildCurrentRunNodes(pipeline.getNodes(), run, runJobMap, runLogs));
        respVO.setEdges(pipeline.getEdges());
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN, key = "#reqVO.applicationEnvId")
    public ApplicationReleaseSubmitBranchRespVO submitApplicationReleaseBranch(
            ApplicationReleaseSubmitBranchReqVO reqVO, Long userId) {
        ApplicationEnvDO applicationEnv = validateApplicationEnvExists(reqVO.getApplicationEnvId());
        Set<Long> targetChangeIds = CollUtil.isEmpty(reqVO.getChangeIds())
                ? Collections.emptySet() : new LinkedHashSet<>(reqVO.getChangeIds());
        List<ChangeDO> targetChanges = validateTargetReleaseChanges(targetChangeIds, applicationEnv);
        Map<Long, ChangeDO> targetChangeMap = targetChanges.stream()
                .collect(Collectors.toMap(ChangeDO::getId, Function.identity()));
        PipelineDefinitionDO pipelineDefinition = validatePublishedPipelineDefinition(applicationEnv.getId());
        PipelineDefinitionVersionDO publishedVersion = validatePublishedPipelineVersion(pipelineDefinition);
        // 提交部署前先停止当前应用环境执行中的流水线：停止失败时抛出业务异常返回前端，不创建新的流水线运行
        stopActivePipelineRuns(applicationEnv.getId(), userId);

        LocalDateTime now = LocalDateTime.now();
        Map<Long, ChangeEnvDO> changeEnvMap = changeEnvMapper.selectListByApplicationEnvId(applicationEnv.getId())
                .stream().collect(Collectors.toMap(ChangeEnvDO::getChangeId, Function.identity(), (first, second) -> first));
        List<Long> unmountedChangeIds = changeEnvMap.values().stream()
                .filter(changeEnv -> ChangeEnvMountStatusEnum.MOUNTED.getStatus().equals(changeEnv.getMountStatus()))
                .map(ChangeEnvDO::getChangeId)
                .filter(changeId -> !targetChangeIds.contains(changeId))
                .toList();

        for (Long changeId : unmountedChangeIds) {
            ChangeEnvDO changeEnv = changeEnvMap.get(changeId);
            fillUnmountedForReleaseSync(changeEnv, userId, now);
            changeEnvMapper.updateById(changeEnv);
        }

        List<ChangeEnvDO> targetChangeEnvs = new ArrayList<>(targetChangeIds.size());
        for (Long changeId : targetChangeIds) {
            ChangeEnvDO changeEnv = changeEnvMap.get(changeId);
            if (changeEnv == null) {
                changeEnv = new ChangeEnvDO();
                changeEnv.setChangeId(changeId);
                changeEnv.setApplicationEnvId(applicationEnv.getId());
                fillMountedForPipelineRun(changeEnv, userId, now);
                changeEnvMapper.insert(changeEnv);
            }
            targetChangeEnvs.add(changeEnv);
        }

        List<ChangeDO> orderedTargetChanges = targetChangeIds.stream().map(targetChangeMap::get).toList();
        ApplicationDO application = validateApplicationExists(applicationEnv.getAppId());
        DeployBranchSelection deployBranchSelection = resolveDeployBranch(applicationEnv, application,
                unmountedChangeIds, now);
        PipelineRunDO pipelineRun = buildPipelineRun(pipelineDefinition, publishedVersion, applicationEnv,
                targetChangeEnvs, orderedTargetChanges, deployBranchSelection.branchName(), userId, now);
        pipelineRunMapper.insert(pipelineRun);

        for (ChangeEnvDO changeEnv : targetChangeEnvs) {
            fillMountedForPipelineRun(changeEnv, userId, now);
            changeEnv.setLastPipelineRunId(pipelineRun.getId());
            changeEnvMapper.updateById(changeEnv);
        }

        // 重新签出部署分支时，旧分支不再被任何后续步骤引用，顺带清理本应用环境历史遗留的部署分支
        if (!deployBranchSelection.reused()) {
            cleanupStaleDeployBranches(application, applicationEnv, deployBranchSelection.branchName());
        }

        ApplicationReleaseSubmitBranchRespVO respVO = new ApplicationReleaseSubmitBranchRespVO();
        respVO.setApplicationEnvId(applicationEnv.getId());
        respVO.setMountedChangeIds(new ArrayList<>(targetChangeIds));
        respVO.setUnmountedChangeIds(unmountedChangeIds);
        respVO.setPipelineRunId(pipelineRun.getId());
        respVO.setRunStatus(pipelineRun.getRunStatus());
        // 异步启动当前应用环境已发布的流水线 YAML。
        schedulePipelineStart(pipelineRun.getId(), new ArrayList<>(targetChangeIds), userId);
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN, key = "#reqVO.applicationEnvId")
    public ApplicationReleaseUploadImageRespVO uploadApplicationReleaseImage(
            ApplicationReleaseUploadImageReqVO reqVO, Long userId) {
        ApplicationEnvDO applicationEnv = validateApplicationEnvExists(reqVO.getApplicationEnvId());
        PipelineDefinitionDO pipelineDefinition = validatePublishedPipelineDefinition(applicationEnv.getId());
        PipelineDefinitionVersionDO publishedVersion = validatePublishedPipelineVersion(pipelineDefinition);
        validateNoActivePipelineRun(applicationEnv.getId());

        LocalDateTime now = LocalDateTime.now();
        PipelineRunDO pipelineRun = buildUploadImagePipelineRun(pipelineDefinition, publishedVersion,
                applicationEnv, reqVO.getFileUrl(), userId, now);
        pipelineRunMapper.insert(pipelineRun);

        ApplicationReleaseUploadImageRespVO respVO = new ApplicationReleaseUploadImageRespVO();
        respVO.setApplicationEnvId(applicationEnv.getId());
        respVO.setPipelineRunId(pipelineRun.getId());
        respVO.setRunStatus(pipelineRun.getRunStatus());
        schedulePipelineStart(pipelineRun.getId(), Collections.emptyList(), userId);
        return respVO;
    }

    @Override
    public ApplicationDO validateApplicationExists(Long id) {
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

    private void validateActive(ChangeDO change) {
        if (!ChangeStatusEnum.ACTIVE.getStatus().equals(change.getStatus())) {
            throw exception(CHANGE_STATUS_NOT_ACTIVE);
        }
    }

    private List<ChangeDO> validateTargetReleaseChanges(Set<Long> targetChangeIds, ApplicationEnvDO applicationEnv) {
        if (CollUtil.isEmpty(targetChangeIds)) {
            return Collections.emptyList();
        }
        List<ChangeDO> changes = changeMapper.selectListByIds(targetChangeIds);
        Map<Long, ChangeDO> changeMap = changes.stream()
                .collect(Collectors.toMap(ChangeDO::getId, Function.identity()));
        if (changeMap.size() != targetChangeIds.size()) {
            throw exception(CHANGE_NOT_EXISTS);
        }
        for (Long changeId : targetChangeIds) {
            ChangeDO change = changeMap.get(changeId);
            validateActive(change);
            if (!applicationEnv.getAppId().equals(change.getAppId())) {
                throw exception(APPLICATION_ENV_NOT_EXISTS);
            }
        }
        return changes;
    }

    private PipelineDefinitionDO validatePublishedPipelineDefinition(Long applicationEnvId) {
        PipelineDefinitionDO pipelineDefinition = pipelineDefinitionMapper.selectByApplicationEnvId(applicationEnvId);
        if (pipelineDefinition == null) {
            throw exception(PIPELINE_DEFINITION_NOT_EXISTS);
        }
        if (pipelineDefinition.getPublishedVersionId() == null) {
            throw exception(PIPELINE_PUBLISHED_VERSION_NOT_EXISTS);
        }
        return pipelineDefinition;
    }

    private PipelineDefinitionVersionDO validatePublishedPipelineVersion(PipelineDefinitionDO pipelineDefinition) {
        PipelineDefinitionVersionDO publishedVersion = pipelineDefinitionVersionMapper
                .selectById(pipelineDefinition.getPublishedVersionId());
        if (publishedVersion == null) {
            throw exception(PIPELINE_PUBLISHED_VERSION_NOT_EXISTS);
        }
        return publishedVersion;
    }

    private void validateNoActivePipelineRun(Long applicationEnvId) {
        if (CollUtil.isNotEmpty(listActivePipelineRuns(applicationEnvId))) {
            throw exception(PIPELINE_RUN_ACTIVE_EXISTS);
        }
    }

    private List<PipelineRunDO> listActivePipelineRuns(Long applicationEnvId) {
        return pipelineRunMapper.selectListByApplicationEnvIdAndStatuses(applicationEnvId,
                List.of(PipelineRunStatusEnum.QUEUED.getStatus(), PipelineRunStatusEnum.RUNNING.getStatus(),
                        PipelineRunStatusEnum.WAITING_INPUT.getStatus()));
    }

    /**
     * 停止当前应用环境正在执行的流水线运行（QUEUED / RUNNING / WAITING_INPUT）。
     *
     * <p>同一应用环境同时只允许一个执行中的流水线运行，因此提交部署时先停止旧运行再创建新运行。
     * 旧运行取消失败时抛出 `PIPELINE_RUN_STOP_FAILED`，由前端提示用户，
     * 避免新旧流水线并发操作同一个应用环境。
     *
     * @param applicationEnvId 应用环境关系编号
     * @param userId           操作用户编号
     */
    private void stopActivePipelineRuns(Long applicationEnvId, Long userId) {
        for (PipelineRunDO activeRun : listActivePipelineRuns(applicationEnvId)) {
            try {
                // 取消在独立事务中提交，保证取消结果不会被外层提交部署事务回滚
                pipelineExecutionService.cancelRun(activeRun.getId(), userId);
            } catch (Exception ex) {
                log.error("[stopActivePipelineRuns][应用环境({}) 流水线运行({}) 停止失败]", applicationEnvId,
                        activeRun.getId(), ex);
                throw exception(PIPELINE_RUN_STOP_FAILED, activeRun.getId());
            }
            PipelineRunDO stoppedRun = pipelineRunMapper.selectById(activeRun.getId());
            if (stoppedRun != null && !PipelineRunStatusEnum.CANCELED.getStatus().equals(stoppedRun.getRunStatus())) {
                log.error("[stopActivePipelineRuns][应用环境({}) 流水线运行({}) 停止后状态仍为({})]", applicationEnvId,
                        activeRun.getId(), stoppedRun.getRunStatus());
                throw exception(PIPELINE_RUN_STOP_FAILED, activeRun.getId());
            }
        }
    }

    private ApplicationReleaseEnvTabRespVO buildReleaseEnvTab(ApplicationEnvDO applicationEnv,
                                                             EnvironmentDO environment,
                                                             PipelineDefinitionDO pipelineDefinition) {
        if (environment == null) {
            throw exception(ENVIRONMENT_NOT_EXISTS);
        }
        ApplicationReleaseEnvTabRespVO respVO = new ApplicationReleaseEnvTabRespVO();
        respVO.setApplicationEnvId(applicationEnv.getId());
        respVO.setAppId(applicationEnv.getAppId());
        respVO.setEnvId(applicationEnv.getEnvId());
        respVO.setEnvKey(environment.getEnvKey());
        respVO.setEnvName(environment.getEnvName());
        respVO.setApplicationEnvName(StrUtil.blankToDefault(applicationEnv.getName(), environment.getEnvName()));
        respVO.setEnvStage(environment.getEnvStage());
        respVO.setInfraType(environment.getInfraType());
        respVO.setDisplayOrder(applicationEnv.getDisplayOrder());
        respVO.setPipelineDefinitionId(pipelineDefinition != null ? pipelineDefinition.getId() : null);
        respVO.setHasPublishedPipeline(pipelineDefinition != null && pipelineDefinition.getPublishedVersionId() != null);
        respVO.setStatus(applicationEnv.getStatus());
        return respVO;
    }

    private ApplicationReleasePipelineRespVO buildReleasePipeline(PipelineDefinitionDO pipelineDefinition) {
        ApplicationReleasePipelineRespVO respVO = new ApplicationReleasePipelineRespVO();
        respVO.setNodes(Collections.emptyList());
        respVO.setEdges(Collections.emptyList());
        if (pipelineDefinition == null) {
            respVO.setEmptyReason(ApplicationReleasePipelineRespVO.EMPTY_REASON_NO_PIPELINE_DEFINITION);
            return respVO;
        }

        respVO.setDefinitionId(pipelineDefinition.getId());
        respVO.setDefinitionName(pipelineDefinition.getName());
        respVO.setDefinitionKey(pipelineDefinition.getDefinitionKey());
        if (pipelineDefinition.getPublishedVersionId() == null) {
            respVO.setEmptyReason(ApplicationReleasePipelineRespVO.EMPTY_REASON_NO_PUBLISHED_VERSION);
            return respVO;
        }
        PipelineDefinitionVersionDO publishedVersion = pipelineDefinitionVersionMapper
                .selectById(pipelineDefinition.getPublishedVersionId());
        if (publishedVersion == null) {
            respVO.setEmptyReason(ApplicationReleasePipelineRespVO.EMPTY_REASON_NO_PUBLISHED_VERSION);
            return respVO;
        }

        respVO.setPublishedVersionId(publishedVersion.getId());
        respVO.setVersionNo(publishedVersion.getVersionNo());
        respVO.setVersionName(publishedVersion.getVersionName());
        respVO.setPublishedAt(publishedVersion.getPublishedAt());
        respVO.setPublishedBy(publishedVersion.getPublishedBy());
        PipelineValidationRespVO validation = new PipelineValidationRespVO();
        PipelineSpec spec = pipelineSpecValidationService.parseSpec(publishedVersion.getSpecJson(), validation);
        if (spec == null) {
            respVO.setEmptyReason(ApplicationReleasePipelineRespVO.EMPTY_REASON_SPEC_INVALID);
            return respVO;
        }
        PipelineSpec.ExecutableGraph graph = spec.toExecutableGraph();
        if (CollUtil.isEmpty(graph.getJobs())) {
            respVO.setEmptyReason(ApplicationReleasePipelineRespVO.EMPTY_REASON_SPEC_INVALID);
            return respVO;
        }
        respVO.setNodes(buildReleasePipelineNodes(graph.getJobs()));
        respVO.setEdges(buildReleasePipelineEdges(graph.getJobs()));
        return respVO;
    }

    private List<ApplicationReleasePipelineNodeRespVO> buildReleasePipelineNodes(
            List<PipelineSpec.ExecutableJob> jobs) {
        List<ApplicationReleasePipelineNodeRespVO> result = new ArrayList<>(jobs.size());
        for (int i = 0; i < jobs.size(); i++) {
            PipelineSpec.ExecutableJob job = jobs.get(i);
            ApplicationReleasePipelineNodeRespVO respVO = new ApplicationReleasePipelineNodeRespVO();
            respVO.setNodeId(job.getJobId());
            respVO.setType(JOB_NODE_TYPE);
            respVO.setNodeType(JOB_NODE_TYPE);
            respVO.setStageId(job.getStageId());
            respVO.setStageName(job.getStageName());
            respVO.setJobId(job.getJobId());
            respVO.setName(job.getName());
            respVO.setEnabled(job.getEnabled());
            respVO.setDisplayOrder(i + 1);
            respVO.setTimeoutSeconds(job.getTimeoutSeconds());
            respVO.setRetryTimes(job.getRetryTimes());
            respVO.setFailStrategy(job.getFailStrategy());
            respVO.setNeeds(job.getNeeds() == null ? Collections.emptyList() : new ArrayList<>(job.getNeeds()));
            respVO.setSteps(buildReleasePipelineStepNodes(job.getSteps()));
            result.add(respVO);
        }
        return result;
    }

    private List<ApplicationReleasePipelineNodeRespVO.Step> buildReleasePipelineStepNodes(
            List<PipelineSpec.ExecutableStep> steps) {
        if (CollUtil.isEmpty(steps)) {
            return Collections.emptyList();
        }
        List<ApplicationReleasePipelineNodeRespVO.Step> result = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            PipelineSpec.ExecutableStep step = steps.get(i);
            ApplicationReleasePipelineNodeRespVO.Step respVO = new ApplicationReleasePipelineNodeRespVO.Step();
            respVO.setStepId(step.getStepId());
            respVO.setName(step.getName());
            respVO.setStep(step.getStep());
            respVO.setEnabled(step.getEnabled());
            respVO.setDisplayOrder(i + 1);
            respVO.setParams(step.getWith());
            respVO.setTimeoutSeconds(step.getTimeoutSeconds());
            respVO.setRetryTimes(step.getRetryTimes());
            respVO.setFailStrategy(step.getFailStrategy());
            result.add(respVO);
        }
        return result;
    }

    private List<ApplicationReleasePipelineEdgeRespVO> buildReleasePipelineEdges(
            List<PipelineSpec.ExecutableJob> jobs) {
        List<ApplicationReleasePipelineEdgeRespVO> edges = new ArrayList<>();
        for (PipelineSpec.ExecutableJob job : jobs) {
            if (CollUtil.isEmpty(job.getNeeds())) {
                continue;
            }
            for (String need : job.getNeeds()) {
                ApplicationReleasePipelineEdgeRespVO respVO = new ApplicationReleasePipelineEdgeRespVO();
                respVO.setSource(need);
                respVO.setTarget(job.getJobId());
                edges.add(respVO);
            }
        }
        return edges;
    }

    private PipelineRunDO getCurrentReleasePipelineRun(Long applicationEnvId) {
        PipelineRunDO activeRun = pipelineRunMapper.selectLatestByApplicationEnvIdAndStatuses(applicationEnvId,
                List.of(PipelineRunStatusEnum.QUEUED.getStatus(), PipelineRunStatusEnum.RUNNING.getStatus(),
                        PipelineRunStatusEnum.WAITING_INPUT.getStatus()));
        return activeRun != null ? activeRun : pipelineRunMapper.selectLatestByApplicationEnvId(applicationEnvId);
    }

    private boolean isRunPolling(PipelineRunDO run) {
        return PipelineRunStatusEnum.QUEUED.getStatus().equals(run.getRunStatus())
                || PipelineRunStatusEnum.RUNNING.getStatus().equals(run.getRunStatus())
                || PipelineRunStatusEnum.WAITING_INPUT.getStatus().equals(run.getRunStatus());
    }

    private List<ApplicationReleaseCurrentRunRespVO.Node> buildCurrentRunNodes(
            List<ApplicationReleasePipelineNodeRespVO> pipelineNodes, PipelineRunDO run,
            Map<String, PipelineRunJobDO> runJobMap, List<PipelineRunLogDO> runLogs) {
        List<ApplicationReleasePipelineNodeRespVO> sourceNodes = CollUtil.isEmpty(pipelineNodes)
                ? Collections.emptyList() : pipelineNodes;
        Map<String, List<PipelineRunLogDO>> logsByJobId = groupRunLogsByJobId(runLogs);
        Map<String, PipelineRunLogDO> runLogMap = indexRunLogsByStepId(runLogs);
        List<ApplicationReleaseCurrentRunRespVO.Node> nodes = new ArrayList<>(sourceNodes.size());
        for (ApplicationReleasePipelineNodeRespVO pipelineNode : sourceNodes) {
            ApplicationReleaseCurrentRunRespVO.Node node = buildPendingRunNode(pipelineNode);
            applyJobRun(node, run, runJobMap.get(pipelineNode.getJobId()), logsByJobId.get(pipelineNode.getJobId()));
            node.setSteps(buildCurrentRunStepNodes(pipelineNode, runLogMap));
            if (!runJobMap.containsKey(pipelineNode.getJobId())) {
                applyJobStateFromSteps(node, run);
            }
            applyActiveStepDetailToJob(node);
            nodes.add(node);
        }
        return nodes;
    }

    private Map<String, List<PipelineRunLogDO>> groupRunLogsByJobId(List<PipelineRunLogDO> runLogs) {
        if (CollUtil.isEmpty(runLogs)) {
            return Collections.emptyMap();
        }
        return runLogs.stream()
                .filter(log -> StrUtil.isNotBlank(log.getJobId()))
                .collect(Collectors.groupingBy(PipelineRunLogDO::getJobId, LinkedHashMap::new, Collectors.toList()));
    }

    private Map<String, PipelineRunLogDO> indexRunLogsByStepId(List<PipelineRunLogDO> runLogs) {
        if (CollUtil.isEmpty(runLogs)) {
            return Collections.emptyMap();
        }
        return runLogs.stream()
                .filter(log -> StrUtil.isNotBlank(log.getStepId()))
                .collect(Collectors.toMap(PipelineRunLogDO::getStepId, Function.identity(), (first, second) -> first,
                        LinkedHashMap::new));
    }

    private void applyJobRun(ApplicationReleaseCurrentRunRespVO.Node node, PipelineRunDO run,
                             PipelineRunJobDO jobRun, List<PipelineRunLogDO> jobLogs) {
        if (jobRun != null) {
            node.setExecutionNodeType(JOB_NODE_TYPE);
            node.setExecutionStatus(jobRun.getStatus());
            node.setSummary(jobRun.getSummary());
            node.setErrorMessage(jobRun.getErrorMessage());
            node.setStartedAt(jobRun.getStartedAt());
            node.setFinishedAt(jobRun.getFinishedAt());
            node.setDurationMillis(jobRun.getDurationMillis());
            node.setHasDetail(run != null);
            node.setDetailType(run == null ? null : DETAIL_TYPE_RUN_LOGS);
            node.setDetailRef(run == null ? null : buildJobDetailRef(run.getId(), node.getJobId()));
            fillNodeDisplay(node, null);
            return;
        }
        if (CollUtil.isEmpty(jobLogs)) {
            if (run != null) {
                node.setHasDetail(true);
                node.setDetailType(DETAIL_TYPE_RUN_LOGS);
                node.setDetailRef(buildJobDetailRef(run.getId(), node.getJobId()));
            }
            return;
        }
        node.setExecutionNodeType(JOB_NODE_TYPE);
        node.setExecutionStatus(resolveJobExecutionStatusFromLogs(jobLogs));
        node.setSummary(resolveJobSummaryFromLogs(jobLogs));
        node.setErrorMessage(resolveJobErrorFromLogs(jobLogs));
        node.setStartedAt(jobLogs.stream().map(PipelineRunLogDO::getStartedAt)
                .filter(time -> time != null).min(LocalDateTime::compareTo).orElse(null));
        node.setFinishedAt(jobLogs.stream().map(PipelineRunLogDO::getFinishedAt)
                .filter(time -> time != null).max(LocalDateTime::compareTo).orElse(null));
        node.setDurationMillis(calculateDurationMillis(node.getStartedAt(), node.getFinishedAt()));
        node.setHasDetail(true);
        node.setDetailType(DETAIL_TYPE_RUN_LOGS);
        node.setDetailRef(buildJobDetailRef(jobLogs.get(0).getPipelineRunId(), node.getJobId()));
        fillNodeDisplay(node, null);
    }

    private Map<String, Object> buildJobDetailRef(Long runId, String jobId) {
        Map<String, Object> detailRef = new LinkedHashMap<>();
        detailRef.put("runId", runId);
        detailRef.put("jobId", jobId);
        return detailRef;
    }

    private String resolveJobExecutionStatusFromLogs(List<PipelineRunLogDO> jobLogs) {
        if (jobLogs.stream().anyMatch(log -> PipelineRunLogStatusEnum.RUNNING.getStatus().equals(log.getStatus()))) {
            return PipelineRunJobStatusEnum.RUNNING.getStatus();
        }
        if (jobLogs.stream().anyMatch(log -> PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(log.getStatus()))) {
            return PipelineRunJobStatusEnum.BLOCKED.getStatus();
        }
        if (jobLogs.stream().anyMatch(log -> PipelineRunLogStatusEnum.FAILED.getStatus().equals(log.getStatus()))) {
            return PipelineRunJobStatusEnum.FAILED.getStatus();
        }
        if (jobLogs.stream().allMatch(log -> PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(log.getStatus()))) {
            return PipelineRunJobStatusEnum.SUCCESS.getStatus();
        }
        return PipelineRunJobStatusEnum.PENDING.getStatus();
    }

    private String resolveJobSummaryFromLogs(List<PipelineRunLogDO> jobLogs) {
        String activeSummary = jobLogs.stream()
                .filter(log -> PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(log.getStatus())
                        || PipelineRunLogStatusEnum.FAILED.getStatus().equals(log.getStatus())
                        || PipelineRunLogStatusEnum.RUNNING.getStatus().equals(log.getStatus()))
                .map(PipelineRunLogDO::getSummary)
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(null);
        if (StrUtil.isNotBlank(activeSummary)) {
            return activeSummary;
        }
        long successCount = jobLogs.stream()
                .filter(log -> PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(log.getStatus())).count();
        return successCount + "/" + jobLogs.size() + " steps success";
    }

    private String resolveJobErrorFromLogs(List<PipelineRunLogDO> jobLogs) {
        return jobLogs.stream()
                .filter(log -> PipelineRunLogStatusEnum.FAILED.getStatus().equals(log.getStatus()))
                .map(log -> StrUtil.blankToDefault(log.getErrorMessage(), log.getSummary()))
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private Long calculateDurationMillis(LocalDateTime startedAt, LocalDateTime finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return Math.max(0L, java.time.Duration.between(startedAt, finishedAt).toMillis());
    }

    private List<ApplicationReleaseCurrentRunRespVO.Step> buildCurrentRunStepNodes(
            ApplicationReleasePipelineNodeRespVO pipelineNode, Map<String, PipelineRunLogDO> runLogMap) {
        if (CollUtil.isEmpty(pipelineNode.getSteps())) {
            return Collections.emptyList();
        }
        List<ApplicationReleaseCurrentRunRespVO.Step> steps = new ArrayList<>(pipelineNode.getSteps().size());
        for (ApplicationReleasePipelineNodeRespVO.Step pipelineStep : pipelineNode.getSteps()) {
            ApplicationReleaseCurrentRunRespVO.Step step = buildPendingRunStep(pipelineStep);
            applyStepRunLog(step, runLogMap.get(pipelineStep.getStepId()));
            steps.add(step);
        }
        return steps;
    }

    private ApplicationReleaseCurrentRunRespVO.Step buildPendingRunStep(ApplicationReleasePipelineNodeRespVO.Step pipelineStep) {
        ApplicationReleaseCurrentRunRespVO.Step step = new ApplicationReleaseCurrentRunRespVO.Step();
        step.setStepId(pipelineStep.getStepId());
        step.setName(pipelineStep.getName());
        step.setStep(pipelineStep.getStep());
        step.setExecutionStatus(PipelineRunLogStatusEnum.PENDING.getStatus());
        step.setStatus(resolveNodeStatus(step.getExecutionStatus()));
        step.setHasDetail(false);
        step.setActions(Collections.emptyList());
        return step;
    }

    private void applyStepRunLog(ApplicationReleaseCurrentRunRespVO.Step step, PipelineRunLogDO runLog) {
        if (runLog == null) {
            return;
        }
        ApplicationReleaseCurrentRunRespVO.Node tempNode = new ApplicationReleaseCurrentRunRespVO.Node();
        tempNode.setNodeId(step.getStepId());
        tempNode.setType(step.getStep());
        tempNode.setName(step.getName());
        tempNode.setExecutionStatus(PipelineRunLogStatusEnum.PENDING.getStatus());
        tempNode.setActions(Collections.emptyList());
        if (PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE.equals(runLog.getNodeType())) {
            applyCodeMergeLog(tempNode, null, runLog);
        } else {
            applyNodeRunLog(tempNode, runLog);
        }
        copyTempNodeToStep(tempNode, step);
    }

    private void copyTempNodeToStep(ApplicationReleaseCurrentRunRespVO.Node node,
                                    ApplicationReleaseCurrentRunRespVO.Step step) {
        step.setRunLogId(node.getRunLogId());
        step.setExecutionStatus(node.getExecutionStatus());
        step.setStatus(node.getStatus());
        step.setMessage(node.getMessage());
        step.setSummary(node.getSummary());
        step.setErrorMessage(node.getErrorMessage());
        step.setStartedAt(node.getStartedAt());
        step.setFinishedAt(node.getFinishedAt());
        step.setDurationMillis(node.getDurationMillis());
        step.setResult(node.getResult());
        step.setHasDetail(node.getHasDetail());
        step.setDetailType(node.getDetailType());
        step.setDetailRef(node.getDetailRef());
        step.setActions(node.getActions());
        step.setConflictCount(node.getConflictCount());
    }

    private void applyActiveStepDetailToJob(ApplicationReleaseCurrentRunRespVO.Node node) {
        if (CollUtil.isEmpty(node.getSteps())) {
            return;
        }
        List<ApplicationReleaseCurrentRunRespVO.Action> stepActions = collectStepActions(node.getSteps());
        if (CollUtil.isNotEmpty(stepActions)) {
            node.setActions(stepActions);
        }
        for (ApplicationReleaseCurrentRunRespVO.Step step : node.getSteps()) {
            if (!PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(step.getExecutionStatus())) {
                continue;
            }
            node.setRunLogId(step.getRunLogId());
            node.setDetailType(step.getDetailType());
            node.setDetailRef(mergeJobDetailRef(node.getDetailRef(), step.getDetailRef()));
            node.setConflictCount(step.getConflictCount());
            node.setMessage(step.getMessage());
            return;
        }
        if (node.getRunLogId() == null) {
            node.getSteps().stream()
                    .filter(step -> step.getRunLogId() != null)
                    .findFirst()
                    .ifPresent(step -> node.setRunLogId(step.getRunLogId()));
        }
    }

    private List<ApplicationReleaseCurrentRunRespVO.Action> collectStepActions(
            List<ApplicationReleaseCurrentRunRespVO.Step> steps) {
        if (CollUtil.isEmpty(steps)) {
            return Collections.emptyList();
        }
        return steps.stream()
                .filter(step -> CollUtil.isNotEmpty(step.getActions()))
                .flatMap(step -> step.getActions().stream())
                .filter(action -> action != null && StrUtil.isNotBlank(action.getCode()))
                .collect(Collectors.toMap(ApplicationReleaseCurrentRunRespVO.Action::getCode, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new))
                .values().stream()
                .toList();
    }

    private void applyJobStateFromSteps(ApplicationReleaseCurrentRunRespVO.Node node, PipelineRunDO run) {
        if (CollUtil.isEmpty(node.getSteps())
                || node.getSteps().stream().allMatch(step -> step.getRunLogId() == null)) {
            return;
        }
        node.setExecutionStatus(resolveJobExecutionStatusFromSteps(node.getSteps()));
        node.setSummary(resolveJobSummaryFromSteps(node.getSteps()));
        node.setErrorMessage(resolveJobErrorFromSteps(node.getSteps()));
        node.setStartedAt(node.getSteps().stream().map(ApplicationReleaseCurrentRunRespVO.Step::getStartedAt)
                .filter(time -> time != null).min(LocalDateTime::compareTo).orElse(null));
        node.setFinishedAt(node.getSteps().stream().map(ApplicationReleaseCurrentRunRespVO.Step::getFinishedAt)
                .filter(time -> time != null).max(LocalDateTime::compareTo).orElse(null));
        node.setDurationMillis(calculateDurationMillis(node.getStartedAt(), node.getFinishedAt()));
        node.setHasDetail(run != null);
        node.setDetailType(run == null ? null : DETAIL_TYPE_RUN_LOGS);
        node.setDetailRef(run == null ? null : buildJobDetailRef(run.getId(), node.getJobId()));
        fillNodeDisplay(node, null);
    }

    private String resolveJobExecutionStatusFromSteps(List<ApplicationReleaseCurrentRunRespVO.Step> steps) {
        if (steps.stream().anyMatch(step -> PipelineRunLogStatusEnum.RUNNING.getStatus().equals(step.getExecutionStatus()))) {
            return PipelineRunJobStatusEnum.RUNNING.getStatus();
        }
        if (steps.stream().anyMatch(step -> PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(step.getExecutionStatus()))) {
            return PipelineRunJobStatusEnum.BLOCKED.getStatus();
        }
        if (steps.stream().anyMatch(step -> PipelineRunLogStatusEnum.FAILED.getStatus().equals(step.getExecutionStatus()))) {
            return PipelineRunJobStatusEnum.FAILED.getStatus();
        }
        if (steps.stream().allMatch(step -> PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(step.getExecutionStatus()))) {
            return PipelineRunJobStatusEnum.SUCCESS.getStatus();
        }
        return PipelineRunJobStatusEnum.PENDING.getStatus();
    }

    private String resolveJobSummaryFromSteps(List<ApplicationReleaseCurrentRunRespVO.Step> steps) {
        String activeSummary = steps.stream()
                .filter(step -> PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(step.getExecutionStatus())
                        || PipelineRunLogStatusEnum.FAILED.getStatus().equals(step.getExecutionStatus())
                        || PipelineRunLogStatusEnum.RUNNING.getStatus().equals(step.getExecutionStatus()))
                .map(ApplicationReleaseCurrentRunRespVO.Step::getSummary)
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(null);
        if (StrUtil.isNotBlank(activeSummary)) {
            return activeSummary;
        }
        long successCount = steps.stream()
                .filter(step -> PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(step.getExecutionStatus())).count();
        return successCount + "/" + steps.size() + " steps success";
    }

    private String resolveJobErrorFromSteps(List<ApplicationReleaseCurrentRunRespVO.Step> steps) {
        return steps.stream()
                .filter(step -> PipelineRunLogStatusEnum.FAILED.getStatus().equals(step.getExecutionStatus()))
                .map(step -> StrUtil.blankToDefault(step.getErrorMessage(), step.getSummary()))
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> mergeJobDetailRef(Map<String, Object> jobDetailRef, Map<String, Object> stepDetailRef) {
        Map<String, Object> detailRef = new LinkedHashMap<>();
        if (jobDetailRef != null) {
            detailRef.putAll(jobDetailRef);
        }
        if (stepDetailRef != null) {
            detailRef.putAll(stepDetailRef);
        }
        return detailRef;
    }

    private void applyNodeRunLog(ApplicationReleaseCurrentRunRespVO.Node node, PipelineRunLogDO runLog) {
        if (runLog == null) {
            return;
        }
        node.setRunLogId(runLog.getId());
        node.setExecutionNodeType(runLog.getNodeType());
        node.setExecutionStatus(runLog.getStatus());
        node.setSummary(runLog.getSummary());
        node.setErrorMessage(runLog.getErrorMessage());
        node.setStartedAt(runLog.getStartedAt());
        node.setFinishedAt(runLog.getFinishedAt());
        node.setDurationMillis(runLog.getDurationMillis());
        Map<String, Object> resultJson = parseRunLogResult(runLog.getResultJson());
        node.setResult(buildCurrentRunResult(resultJson));
        node.setActions(readCurrentRunActions(resultJson));
        node.setHasDetail(true);
        node.setDetailType(resolveDetailType(runLog, resultJson));
        if (DETAIL_TYPE_DEPLOYMENT_ORDER.equals(node.getDetailType())) {
            node.setDetailRef(buildDeploymentOrderDetailRef(node, runLog, resultJson));
            if (CollUtil.isEmpty(node.getActions())) {
                node.setActions(List.of(buildOpenDeploymentOrderAction(resultJson)));
            }
        }
        fillNodeDisplay(node, runLog);
        if (PipelineNodeRegistryServiceImpl.TYPE_APPROVAL.equals(runLog.getNodeType())) {
            applyApprovalDetail(node, runLog);
        }
    }

    private ApplicationReleaseCurrentRunRespVO.Node buildPendingRunNode(ApplicationReleasePipelineNodeRespVO pipelineNode) {
        ApplicationReleaseCurrentRunRespVO.Node node = new ApplicationReleaseCurrentRunRespVO.Node();
        node.setNodeId(pipelineNode.getNodeId());
        node.setType(pipelineNode.getType());
        node.setNodeType(pipelineNode.getNodeType());
        node.setStageId(pipelineNode.getStageId());
        node.setStageName(pipelineNode.getStageName());
        node.setJobId(pipelineNode.getJobId());
        node.setName(pipelineNode.getName());
        node.setDisplayOrder(pipelineNode.getDisplayOrder());
        node.setEnabled(pipelineNode.getEnabled());
        node.setExecutionNodeType(JOB_NODE_TYPE);
        node.setExecutionStatus(PipelineRunLogStatusEnum.PENDING.getStatus());
        fillNodeDisplay(node, null);
        node.setHasDetail(false);
        return node;
    }

    private void applyCodeMergeLog(ApplicationReleaseCurrentRunRespVO.Node node, PipelineRunDO run,
                                   PipelineRunLogDO codeMergeLog) {
        node.setExecutionNodeType(PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE);
        if (codeMergeLog == null) {
            if (run != null && isRunPolling(run)) {
                node.setExecutionStatus(PipelineRunLogStatusEnum.RUNNING.getStatus());
                node.setSummary("等待代码合并开始");
                fillNodeDisplay(node, null);
            }
            return;
        }
        node.setRunLogId(codeMergeLog.getId());
        node.setExecutionStatus(codeMergeLog.getStatus());
        node.setSummary(codeMergeLog.getSummary());
        node.setErrorMessage(codeMergeLog.getErrorMessage());
        node.setStartedAt(codeMergeLog.getStartedAt());
        node.setFinishedAt(codeMergeLog.getFinishedAt());
        node.setDurationMillis(codeMergeLog.getDurationMillis());
        Map<String, Object> resultJson = parseRunLogResult(codeMergeLog.getResultJson());
        node.setResult(buildCurrentRunResult(resultJson));
        node.setActions(readCurrentRunActions(resultJson));
        node.setHasDetail(true);
        boolean waitingInput = PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(codeMergeLog.getStatus());
        int conflictCount = waitingInput ? countConflicts(codeMergeLog.getContextJson()) : 0;
        node.setDetailType(waitingInput ? DETAIL_TYPE_CODE_MERGE : DETAIL_TYPE_RUN_LOGS);
        node.setConflictCount(conflictCount);
        node.setDetailRef(waitingInput ? buildCodeMergeDetailRef(node, codeMergeLog, conflictCount) : null);
        if (waitingInput && CollUtil.isEmpty(node.getActions())) {
            node.setActions(List.of(buildResolveCodeConflictAction(node, codeMergeLog)));
        }
        fillNodeDisplay(node, codeMergeLog);
    }

    private void fillNodeDisplay(ApplicationReleaseCurrentRunRespVO.Node node, PipelineRunLogDO runLog) {
        node.setStatus(resolveNodeStatus(node.getExecutionStatus()));
        node.setMessage(resolveNodeMessage(node, runLog));
        if (node.getActions() == null) {
            node.setActions(Collections.emptyList());
        }
    }

    private String resolveNodeStatus(String executionStatus) {
        if (PipelineRunLogStatusEnum.PENDING.getStatus().equals(executionStatus)) {
            return NODE_STATUS_NOT_STARTED;
        }
        if (PipelineRunLogStatusEnum.RUNNING.getStatus().equals(executionStatus)) {
            return NODE_STATUS_RUNNING;
        }
        if (PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(executionStatus)) {
            return NODE_STATUS_BLOCKED;
        }
        if (PipelineRunJobStatusEnum.PENDING.getStatus().equals(executionStatus)) {
            return NODE_STATUS_NOT_STARTED;
        }
        if (PipelineRunJobStatusEnum.RUNNING.getStatus().equals(executionStatus)) {
            return NODE_STATUS_RUNNING;
        }
        if (PipelineRunJobStatusEnum.BLOCKED.getStatus().equals(executionStatus)) {
            return NODE_STATUS_BLOCKED;
        }
        return NODE_STATUS_COMPLETED;
    }

    private String resolveNodeMessage(ApplicationReleaseCurrentRunRespVO.Node node, PipelineRunLogDO runLog) {
        if (runLog != null && StrUtil.isNotBlank(runLog.getSummary())) {
            return runLog.getSummary();
        }
        if (runLog != null && StrUtil.isNotBlank(runLog.getErrorMessage())) {
            return runLog.getErrorMessage();
        }
        if (StrUtil.isNotBlank(node.getSummary())) {
            return node.getSummary();
        }
        return null;
    }

    private void applyApprovalDetail(ApplicationReleaseCurrentRunRespVO.Node node, PipelineRunLogDO runLog) {
        PipelineApprovalContext context = parseApprovalContext(runLog);
        node.setDetailRef(buildApprovalDetailRef(node, runLog, context));
        if (PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(runLog.getStatus())
                && StrUtil.isNotBlank(context.getProcessInstanceId()) && CollUtil.isEmpty(node.getActions())) {
            node.setActions(List.of(buildOpenApprovalDetailAction(context)));
        }
    }

    private Map<String, Object> parseRunLogResult(String resultJsonText) {
        Map<String, Object> source = JsonUtils.parseMap(resultJsonText);
        return CollUtil.isEmpty(source) ? Collections.emptyMap() : source;
    }

    private Map<String, Object> buildCurrentRunResult(Map<String, Object> source) {
        if (CollUtil.isEmpty(source)) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (StrUtil.isBlank(key) || CURRENT_RUN_RESULT_BLOCKED_KEYS.contains(key)) {
                continue;
            }
            if (CURRENT_RUN_RESULT_ALLOWED_KEYS.contains(key) && isCurrentRunLightValue(entry.getValue())) {
                result.put(key, entry.getValue());
            }
        }
        return result.isEmpty() ? null : result;
    }

    private boolean isCurrentRunLightValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence text) {
            return text.length() <= 512;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            return map.size() <= 20;
        }
        if (value instanceof List<?> list) {
            return list.size() <= 20;
        }
        return false;
    }

    private List<ApplicationReleaseCurrentRunRespVO.Action> readCurrentRunActions(Map<String, Object> result) {
        if (CollUtil.isEmpty(result)) {
            return Collections.emptyList();
        }
        Object actions = result.get(RESULT_ACTIONS_KEY);
        if (!(actions instanceof List<?> list) || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream()
                .filter(action -> action instanceof Map<?, ?>)
                .map(action -> convertCurrentRunAction((Map<?, ?>) action))
                .filter(Objects::nonNull)
                .toList();
    }

    private ApplicationReleaseCurrentRunRespVO.Action convertCurrentRunAction(Map<?, ?> source) {
        String key = stringValue(source.get("key"));
        String type = stringValue(source.get("type"));
        if (StrUtil.isBlank(key) || StrUtil.isBlank(type)) {
            return null;
        }
        ApplicationReleaseCurrentRunRespVO.Action action = new ApplicationReleaseCurrentRunRespVO.Action();
        action.setCode(key);
        action.setKey(key);
        action.setLabel(stringValue(source.get("label")));
        action.setType(type);
        action.setStyle(stringValue(source.get("style")));
        action.setPermission(stringValue(source.get("permission")));
        action.setEnabled(booleanValue(source.get("enabled")));
        action.setDisabledReason(stringValue(source.get("disabledReason")));
        action.setPayload(mapValue(source.get("payload")));
        action.setOrder(integerValue(source.get("order")));
        action.setTarget(convertCurrentRunActionTarget(type, source.get("target")));
        return action;
    }

    private ApplicationReleaseCurrentRunRespVO.ActionTarget convertCurrentRunActionTarget(String type, Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return null;
        }
        ApplicationReleaseCurrentRunRespVO.ActionTarget target = new ApplicationReleaseCurrentRunRespVO.ActionTarget();
        target.setType(type);
        target.setRouteName(stringValue(source.get("routeName")));
        target.setPath(stringValue(source.get("path")));
        target.setUrl(stringValue(source.get("url")));
        target.setApiPath(stringValue(source.get("apiPath")));
        target.setMethod(stringValue(source.get("method")));
        target.setFileName(stringValue(source.get("fileName")));
        target.setConfirmText(stringValue(source.get("confirmText")));
        target.setParams(mapValue(source.get("params")));
        target.setQuery(mapValue(source.get("query")));
        target.setBody(mapValue(source.get("body")));
        return target;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Boolean booleanValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.valueOf(String.valueOf(value));
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> source) || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, mapValue) -> {
            if (key != null && isCurrentRunLightValue(mapValue)) {
                result.put(String.valueOf(key), mapValue);
            }
        });
        return result;
    }

    private Map<String, Object> buildCodeMergeDetailRef(ApplicationReleaseCurrentRunRespVO.Node node,
                                                        PipelineRunLogDO runLog, int conflictCount) {
        Map<String, Object> detailRef = new LinkedHashMap<>();
        detailRef.put("runId", runLog.getPipelineRunId());
        detailRef.put("nodeId", StrUtil.blankToDefault(runLog.getNodeId(), node.getNodeId()));
        detailRef.put("runLogId", runLog.getId());
        detailRef.put("conflictCount", conflictCount);
        return detailRef;
    }

    private Map<String, Object> buildApprovalDetailRef(ApplicationReleaseCurrentRunRespVO.Node node,
                                                       PipelineRunLogDO runLog, PipelineApprovalContext context) {
        Map<String, Object> detailRef = new LinkedHashMap<>();
        detailRef.put("runId", runLog.getPipelineRunId());
        detailRef.put("nodeId", StrUtil.blankToDefault(runLog.getNodeId(), node.getNodeId()));
        detailRef.put("runLogId", runLog.getId());
        if (StrUtil.isNotBlank(context.getProcessInstanceId())) {
            detailRef.put("processInstanceId", context.getProcessInstanceId());
        }
        if (StrUtil.isNotBlank(context.getStatus())) {
            detailRef.put("approvalStatus", context.getStatus());
        }
        return detailRef;
    }

    private String resolveDetailType(PipelineRunLogDO runLog, Map<String, Object> resultJson) {
        if (PipelineNodeRegistryServiceImpl.TYPE_APPROVAL.equals(runLog.getNodeType())) {
            return DETAIL_TYPE_APPROVAL;
        }
        if (isDeploymentOrderStep(runLog) && resultJson.get("deploymentOrderId") != null) {
            return DETAIL_TYPE_DEPLOYMENT_ORDER;
        }
        return DETAIL_TYPE_RUN_LOGS;
    }

    private boolean isDeploymentOrderStep(PipelineRunLogDO runLog) {
        return PipelineNodeRegistryServiceImpl.TYPE_K8S_DEPLOY.equals(runLog.getNodeType())
                || PipelineNodeRegistryServiceImpl.TYPE_K8S_IMAGE_UPGRADE.equals(runLog.getNodeType());
    }

    private Map<String, Object> buildDeploymentOrderDetailRef(ApplicationReleaseCurrentRunRespVO.Node node,
                                                             PipelineRunLogDO runLog,
                                                             Map<String, Object> resultJson) {
        Map<String, Object> detailRef = new LinkedHashMap<>();
        detailRef.put("runId", runLog.getPipelineRunId());
        detailRef.put("nodeId", StrUtil.blankToDefault(runLog.getNodeId(), node.getNodeId()));
        detailRef.put("runLogId", runLog.getId());
        detailRef.put("deploymentOrderId", resultJson.get("deploymentOrderId"));
        detailRef.put("apiPath", "/devops/deployment-order/" + resultJson.get("deploymentOrderId"));
        detailRef.put("method", "GET");
        return detailRef;
    }

    private ApplicationReleaseCurrentRunRespVO.Action buildOpenDeploymentOrderAction(Map<String, Object> resultJson) {
        Object deploymentOrderId = resultJson.get("deploymentOrderId");
        ApplicationReleaseCurrentRunRespVO.Action action = new ApplicationReleaseCurrentRunRespVO.Action();
        action.setCode(PipelineStepActionKeyConstants.DEPLOYMENT_ORDER_OPEN_DETAIL);
        action.setKey(PipelineStepActionKeyConstants.DEPLOYMENT_ORDER_OPEN_DETAIL);
        action.setLabel("查看部署单");
        action.setType(ACTION_TARGET_API);
        action.setStyle("PRIMARY");
        action.setPermission("devops:pipeline:query");
        action.setEnabled(true);
        action.setOrder(150);

        ApplicationReleaseCurrentRunRespVO.ActionTarget target = new ApplicationReleaseCurrentRunRespVO.ActionTarget();
        target.setType(ACTION_TARGET_API);
        target.setApiPath("/devops/deployment-order/" + deploymentOrderId);
        target.setMethod("GET");
        action.setTarget(target);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deploymentOrderId", deploymentOrderId);
        payload.put("deployStatus", resultJson.get("deployStatus"));
        payload.put("currentStage", resultJson.get("currentStage"));
        payload.put("modal", "DEPLOYMENT_ORDER_DETAIL");
        action.setPayload(payload);
        return action;
    }

    private ApplicationReleaseCurrentRunRespVO.Action buildResolveCodeConflictAction(
            ApplicationReleaseCurrentRunRespVO.Node node, PipelineRunLogDO runLog) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("runId", runLog.getPipelineRunId());
        params.put("nodeId", StrUtil.blankToDefault(runLog.getNodeId(), node.getNodeId()));
        return buildRouteAction(ACTION_RESOLVE_CODE_CONFLICT, "解决冲突", "primary",
                "/devops/pipeline-run/" + runLog.getPipelineRunId() + "/code-merge/conflicts", params);
    }

    private ApplicationReleaseCurrentRunRespVO.Action buildOpenApprovalDetailAction(PipelineApprovalContext context) {
        ApplicationReleaseCurrentRunRespVO.Action action = buildRouteAction(ACTION_OPEN_APPROVAL_DETAIL, "去审批",
                "primary", "/bpm/process-instance/detail", new LinkedHashMap<>());
        action.getTarget().setQuery(Map.of("id", context.getProcessInstanceId()));
        return action;
    }

    private ApplicationReleaseCurrentRunRespVO.Action buildRouteAction(String code, String label, String style,
                                                                       String path, Map<String, Object> params) {
        ApplicationReleaseCurrentRunRespVO.Action action = new ApplicationReleaseCurrentRunRespVO.Action();
        action.setCode(code);
        action.setKey(code);
        action.setLabel(label);
        action.setType(ACTION_TARGET_ROUTE);
        action.setStyle(style);
        action.setEnabled(true);
        ApplicationReleaseCurrentRunRespVO.ActionTarget target = new ApplicationReleaseCurrentRunRespVO.ActionTarget();
        target.setType(ACTION_TARGET_ROUTE);
        target.setPath(path);
        target.setParams(params);
        action.setTarget(target);
        return action;
    }

    private PipelineApprovalContext parseApprovalContext(PipelineRunLogDO runLog) {
        PipelineApprovalContext context = JsonUtils.parseObject(runLog.getContextJson(), PipelineApprovalContext.class);
        return context == null ? new PipelineApprovalContext() : context;
    }

    private Integer countConflicts(String contextJson) {
        Map<String, Object> context = JsonUtils.parseMap(contextJson);
        if (context == null) {
            return 0;
        }
        Object conflicts = context.get("conflicts");
        return conflicts instanceof List<?> list ? list.size() : 0;
    }

    private void fillReleaseBranches(ApplicationReleaseEnvDetailRespVO detail, ApplicationEnvDO applicationEnv) {
        ReleaseBranches releaseBranches = buildReleaseBranches(applicationEnv);
        detail.setMountedBranches(releaseBranches.getMountedBranches());
        detail.setUnmountedBranches(releaseBranches.getUnmountedBranches());
    }

    private ReleaseBranches buildReleaseBranches(ApplicationEnvDO applicationEnv) {
        List<ChangeDO> activeChanges = changeMapper.selectListByAppIdAndStatus(
                applicationEnv.getAppId(), ChangeStatusEnum.ACTIVE.getStatus());
        if (CollUtil.isEmpty(activeChanges)) {
            return new ReleaseBranches(Collections.emptyList(), Collections.emptyList());
        }
        Map<Long, ChangeEnvDO> changeEnvMap = changeEnvMapper.selectListByApplicationEnvId(applicationEnv.getId())
                .stream().collect(Collectors.toMap(ChangeEnvDO::getChangeId, Function.identity(), (first, second) -> first));

        List<ApplicationReleaseBranchRespVO> mountedBranches = new ArrayList<>();
        List<ApplicationReleaseBranchRespVO> unmountedBranches = new ArrayList<>();
        for (ChangeDO change : activeChanges) {
            ChangeEnvDO changeEnv = changeEnvMap.get(change.getId());
            ApplicationReleaseBranchRespVO branch = buildReleaseBranch(change, changeEnv);
            if (changeEnv != null && ChangeEnvMountStatusEnum.MOUNTED.getStatus().equals(changeEnv.getMountStatus())) {
                mountedBranches.add(branch);
            } else {
                unmountedBranches.add(branch);
            }
        }
        Comparator<ApplicationReleaseBranchRespVO> branchComparator = Comparator
                .comparing(ApplicationReleaseBranchRespVO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ApplicationReleaseBranchRespVO::getChangeId, Comparator.nullsLast(Comparator.reverseOrder()));
        mountedBranches.sort(branchComparator);
        unmountedBranches.sort(branchComparator);
        return new ReleaseBranches(mountedBranches, unmountedBranches);
    }

    private ApplicationReleaseBranchRespVO buildReleaseBranch(ChangeDO change, ChangeEnvDO changeEnv) {
        ApplicationReleaseBranchRespVO respVO = new ApplicationReleaseBranchRespVO();
        respVO.setChangeId(change.getId());
        respVO.setChangeKey(change.getChangeKey());
        respVO.setTitle(change.getTitle());
        respVO.setBranchName(change.getBranchName());
        respVO.setSourceBaseBranchName(change.getSourceBaseBranchName());
        respVO.setOwnerUserId(change.getOwnerUserId());
        respVO.setTesterUserId(change.getTesterUserId());
        respVO.setTestPassed(change.getTestPassed());
        respVO.setTestPassedCommitSha(change.getTestPassedCommitSha());
        respVO.setCodeReviewerUserId(change.getCodeReviewerUserId());
        respVO.setCodeReviewStatus(change.getCodeReviewStatus());
        respVO.setCodeReviewPassedCommitSha(change.getCodeReviewPassedCommitSha());
        respVO.setLatestCommitSha(change.getLatestCommitSha());
        respVO.setLatestCommitMessage(change.getLatestCommitMessage());
        respVO.setLatestCommitAt(change.getLatestCommitAt());
        respVO.setCreateTime(change.getCreateTime());
        if (changeEnv == null) {
            return respVO;
        }
        respVO.setChangeEnvId(changeEnv.getId());
        respVO.setMountStatus(changeEnv.getMountStatus());
        respVO.setMountedAt(changeEnv.getMountedAt());
        respVO.setMountedBy(changeEnv.getMountedBy());
        respVO.setLastPipelineRunId(changeEnv.getLastPipelineRunId());
        respVO.setLastMergeStatus(changeEnv.getLastMergeStatus());
        respVO.setLastBuildStatus(changeEnv.getLastBuildStatus());
        respVO.setLastTestStatus(changeEnv.getLastTestStatus());
        respVO.setLastDeployStatus(changeEnv.getLastDeployStatus());
        respVO.setLastErrorMessage(changeEnv.getLastErrorMessage());
        respVO.setApprovalStatus(changeEnv.getApprovalStatus());
        respVO.setIncludedInCurrentSnapshot(changeEnv.getIncludedInCurrentSnapshot());
        return respVO;
    }

    private void fillMountedForPipelineRun(ChangeEnvDO changeEnv, Long userId, LocalDateTime now) {
        changeEnv.setMountStatus(ChangeEnvMountStatusEnum.MOUNTED.getStatus());
        changeEnv.setMountedAt(now);
        changeEnv.setMountedBy(userId);
        changeEnv.setUnmountedAt(null);
        changeEnv.setUnmountedBy(null);
        changeEnv.setUnmountedReason(null);
        changeEnv.setLastMergeStatus(MergeStatusEnum.PENDING.getStatus());
        changeEnv.setLastBuildStatus(PipelineStatusEnum.PENDING.getStatus());
        changeEnv.setLastTestStatus(PipelineStatusEnum.PENDING.getStatus());
        changeEnv.setLastDeployStatus(PipelineStatusEnum.PENDING.getStatus());
        changeEnv.setLastErrorMessage(null);
        changeEnv.setApprovalStatus(ApprovalStatusEnum.NONE.getStatus());
        changeEnv.setIncludedInCurrentSnapshot(false);
    }

    private void fillUnmountedForReleaseSync(ChangeEnvDO changeEnv, Long userId, LocalDateTime now) {
        changeEnv.setMountStatus(ChangeEnvMountStatusEnum.UNMOUNTED.getStatus());
        changeEnv.setUnmountedAt(now);
        changeEnv.setUnmountedBy(userId);
        changeEnv.setUnmountedReason("RELEASE_TARGET_SET_SYNC");
        changeEnv.setIncludedInCurrentSnapshot(false);
    }

    private PipelineRunDO buildPipelineRun(PipelineDefinitionDO pipelineDefinition,
                                           PipelineDefinitionVersionDO publishedVersion,
                                           ApplicationEnvDO applicationEnv,
                                           List<ChangeEnvDO> changeEnvs,
                                           List<ChangeDO> changes,
                                           String deployBranch,
                                           Long userId,
                                           LocalDateTime now) {
        PipelineRunDO pipelineRun = new PipelineRunDO();
        pipelineRun.setDefinitionId(pipelineDefinition.getId());
        pipelineRun.setDefinitionVersionId(publishedVersion.getId());
        pipelineRun.setAppId(applicationEnv.getAppId());
        pipelineRun.setApplicationEnvId(applicationEnv.getId());
        if (CollUtil.isNotEmpty(changes)) {
            pipelineRun.setChangeId(changes.get(0).getId());
            pipelineRun.setChangeEnvId(changeEnvs.get(0).getId());
            pipelineRun.setCommitSha(changes.get(0).getLatestCommitSha());
        }
        pipelineRun.setBranchName(deployBranch);
        pipelineRun.setChangeSnapshotJson(JsonUtils.toJsonString(buildRunChangeSnapshots(changes)));
        pipelineRun.setRunStatus(PipelineRunStatusEnum.RUNNING.getStatus());
        pipelineRun.setTriggerType(TRIGGER_TYPE_APPLICATION_RELEASE_TAB);
        pipelineRun.setTriggerUserId(userId);
        pipelineRun.setTriggeredAt(now);
        pipelineRun.setStartedAt(now);
        return pipelineRun;
    }

    private PipelineRunDO buildUploadImagePipelineRun(PipelineDefinitionDO pipelineDefinition,
                                                      PipelineDefinitionVersionDO publishedVersion,
                                                      ApplicationEnvDO applicationEnv,
                                                      String fileUrl,
                                                      Long userId,
                                                      LocalDateTime now) {
        PipelineRunDO pipelineRun = new PipelineRunDO();
        pipelineRun.setDefinitionId(pipelineDefinition.getId());
        pipelineRun.setDefinitionVersionId(publishedVersion.getId());
        pipelineRun.setAppId(applicationEnv.getAppId());
        pipelineRun.setApplicationEnvId(applicationEnv.getId());
        pipelineRun.setBranchName(buildUploadImageBranch(applicationEnv, now));
        pipelineRun.setChangeSnapshotJson(JsonUtils.toJsonString(Collections.emptyList()));
        pipelineRun.setInputContextJson(JsonUtils.toJsonString(Map.of(
                "fileUrl", fileUrl,
                "triggerMode", TRIGGER_MODE_UPLOAD_IMAGE)));
        pipelineRun.setRunStatus(PipelineRunStatusEnum.RUNNING.getStatus());
        pipelineRun.setTriggerType(TRIGGER_TYPE_APPLICATION_UPLOAD_IMAGE);
        pipelineRun.setTriggerUserId(userId);
        pipelineRun.setTriggeredAt(now);
        pipelineRun.setStartedAt(now);
        return pipelineRun;
    }

    private String buildUploadImageBranch(ApplicationEnvDO applicationEnv, LocalDateTime now) {
        EnvironmentDO environment = validateEnvironmentExists(applicationEnv.getEnvId());
        return "upload-image/" + sanitizeRefPart(environment.getEnvKey()) + "/"
                + DEPLOY_BRANCH_TIMESTAMP_FORMATTER.format(now);
    }

    private DeployBranchSelection resolveDeployBranch(ApplicationEnvDO applicationEnv, ApplicationDO application,
                                                      List<Long> unmountedChangeIds, LocalDateTime now) {
        PipelineRunDO latestReleaseRun = pipelineRunMapper.selectLatestByApplicationEnvIdAndBranchPrefix(
                applicationEnv.getId(), DEPLOY_BRANCH_PREFIX);
        if (latestReleaseRun != null
                && StrUtil.isNotBlank(latestReleaseRun.getBranchName())
                && CollUtil.isEmpty(unmountedChangeIds)
                && !isBaseBranchOutdated(application, latestReleaseRun)) {
            return new DeployBranchSelection(latestReleaseRun.getBranchName(), true);
        }
        EnvironmentDO environment = validateEnvironmentExists(applicationEnv.getEnvId());
        return new DeployBranchSelection(DEPLOY_BRANCH_PREFIX + sanitizeRefPart(environment.getEnvKey()) + "/"
                + DEPLOY_BRANCH_TIMESTAMP_FORMATTER.format(now), false);
    }

    /**
     * 清理本应用环境历史遗留的部署分支。
     *
     * <p>仅在重新签出（不复用）部署分支时调用。归属证明取本地流水线运行记录而非远端分支名前缀：
     * 运行记录自带 `application_env_id` 约束，不会误删其他应用环境的部署分支。
     *
     * <p>清理失败仅记录日志，不阻断发布提交：旧分支已无功能引用（部署单不记录分支名），
     * 且下次重新签出时会按全量查询再次尝试，具备自愈性。
     *
     * @param application         应用
     * @param applicationEnv      应用环境关系
     * @param currentDeployBranch 本次新建的部署分支，从清理候选中排除。同一秒内连续两次提交会
     *                            生成同名分支，此时必须保留而非删除
     */
    private void cleanupStaleDeployBranches(ApplicationDO application, ApplicationEnvDO applicationEnv,
                                            String currentDeployBranch) {
        List<PipelineRunDO> historyRuns = pipelineRunMapper.selectListByApplicationEnvIdAndBranchPrefix(
                applicationEnv.getId(), DEPLOY_BRANCH_PREFIX);
        LinkedHashSet<String> staleBranches = historyRuns.stream()
                .map(PipelineRunDO::getBranchName)
                .filter(StrUtil::isNotBlank)
                .filter(branchName -> !branchName.equals(currentDeployBranch))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (CollUtil.isEmpty(staleBranches)) {
            return;
        }
        List<String> cleanedBranches = new ArrayList<>();
        for (String staleBranch : staleBranches) {
            try {
                repositoryProviderService.deleteRepositoryBranch(application.getRepositoryProviderId(),
                        application.getRepoIdentifier(), staleBranch);
                cleanedBranches.add(staleBranch);
            } catch (Exception ex) {
                log.warn("[cleanupStaleDeployBranches][应用环境({}) 部署分支({}) 清理失败]",
                        applicationEnv.getId(), staleBranch, ex);
            }
        }
        if (CollUtil.isNotEmpty(cleanedBranches)) {
            log.info("[cleanupStaleDeployBranches][应用环境({}) 清理历史部署分支({})]",
                    applicationEnv.getId(), cleanedBranches);
        }
    }

    /**
     * 部署分支决策结果。
     *
     * <p>判定条件保留在 `resolveDeployBranch` 内部，调用方仅根据 `reused` 决定是否清理历史部署分支，
     * 避免在调用方重复推导 `unmountedChangeIds` / 基线过期条件而导致逻辑漂移。
     *
     * @param branchName 本次运行使用的部署分支名
     * @param reused     是否复用了本应用环境已有的部署分支
     */
    private record DeployBranchSelection(String branchName, boolean reused) {
    }

    private boolean isDeployBranch(String branchName) {
        return StrUtil.startWith(branchName, DEPLOY_BRANCH_PREFIX);
    }

    private String sanitizeRefPart(String value) {
        return StrUtil.blankToDefault(value, "unknown")
                .replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("-+", "-");
    }

    private List<ApplicationReleaseChangeSnapshotRespVO> parseRunChangeSnapshots(PipelineRunDO run) {
        List<PipelineRunChangeSnapshotContext> snapshots = JsonUtils.parseArray(run.getChangeSnapshotJson(),
                PipelineRunChangeSnapshotContext.class);
        if (CollUtil.isEmpty(snapshots)) {
            return Collections.emptyList();
        }
        return snapshots.stream().map(snapshot -> {
            ApplicationReleaseChangeSnapshotRespVO respVO = new ApplicationReleaseChangeSnapshotRespVO();
            respVO.setChangeId(snapshot.getChangeId());
            respVO.setCommitSha(snapshot.getCommitSha());
            return respVO;
        }).toList();
    }

    private void fillBaseBranchStatus(ApplicationReleaseCurrentRunRespVO respVO,
                                      ApplicationDO application,
                                      PipelineRunDO run) {
        respVO.setBaseBranchLatestCommitSha(application.getBaseBranchLatestCommitSha());
        respVO.setBaseBranchLatestCommitMessage(application.getBaseBranchLatestCommitMessage());
        respVO.setBaseBranchLatestCommitAt(application.getBaseBranchLatestCommitAt());
        if (run == null) {
            respVO.setBaseBranchOutdated(false);
            return;
        }
        respVO.setBaseBranchName(run.getBaseBranchName());
        respVO.setBaseCommitSha(run.getBaseCommitSha());
        respVO.setBaseBranchOutdated(isBaseBranchOutdated(application, run));
    }

    private boolean isBaseBranchOutdated(ApplicationDO application, PipelineRunDO run) {
        return Objects.equals(run.getBaseBranchName(), application.getDefaultBranchName())
                && StrUtil.isNotBlank(run.getBaseCommitSha())
                && StrUtil.isNotBlank(application.getBaseBranchLatestCommitSha())
                && !Objects.equals(run.getBaseCommitSha(), application.getBaseBranchLatestCommitSha());
    }

    private List<PipelineRunChangeSnapshotContext> buildRunChangeSnapshots(List<ChangeDO> changes) {
        return changes.stream()
                .map(change -> new PipelineRunChangeSnapshotContext(change.getId(), change.getLatestCommitSha()))
                .toList();
    }

    /**
     * 调度流水线执行：在事务提交后异步启动已发布流水线 YAML。
     */
    private void schedulePipelineStart(Long pipelineRunId, List<Long> changeIds, Long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    pipelineExecutionAsyncService.startPipelineAsync(pipelineRunId, changeIds, userId);
                }
            });
            return;
        }
        pipelineExecutionAsyncService.startPipelineAsync(pipelineRunId, changeIds, userId);
    }

    private void validateApplicationUnique(Long id, String appKey, Long repositoryProviderId, String repoIdentifier) {
        ApplicationDO appKeyApplication = applicationMapper.selectByAppKey(appKey);
        if (appKeyApplication != null && !appKeyApplication.getId().equals(id)) {
            throw exception(APPLICATION_APP_KEY_DUPLICATE);
        }
        ApplicationDO repoIdentifierApplication = applicationMapper
                .selectByRepositoryProviderIdAndRepoIdentifier(repositoryProviderId, repoIdentifier);
        if (repoIdentifierApplication != null && !repoIdentifierApplication.getId().equals(id)) {
            throw exception(APPLICATION_REPO_IDENTIFIER_DUPLICATE);
        }
    }

    private void validateApplicationEnvList(List<ApplicationEnvSaveReqVO> envs) {
        if (CollUtil.isEmpty(envs)) {
            return;
        }
        Set<Long> envIds = new HashSet<>();
        for (ApplicationEnvSaveReqVO env : envs) {
            if (!envIds.add(env.getEnvId())) {
                throw exception(APPLICATION_ENV_DUPLICATE);
            }
            if (environmentMapper.selectById(env.getEnvId()) == null) {
                throw exception(ENVIRONMENT_NOT_EXISTS);
            }
        }
    }

    private static class ReleaseBranches {

        private final List<ApplicationReleaseBranchRespVO> mountedBranches;
        private final List<ApplicationReleaseBranchRespVO> unmountedBranches;

        ReleaseBranches(List<ApplicationReleaseBranchRespVO> mountedBranches,
                        List<ApplicationReleaseBranchRespVO> unmountedBranches) {
            this.mountedBranches = mountedBranches;
            this.unmountedBranches = unmountedBranches;
        }

        List<ApplicationReleaseBranchRespVO> getMountedBranches() {
            return mountedBranches;
        }

        List<ApplicationReleaseBranchRespVO> getUnmountedBranches() {
            return unmountedBranches;
        }

    }

}
