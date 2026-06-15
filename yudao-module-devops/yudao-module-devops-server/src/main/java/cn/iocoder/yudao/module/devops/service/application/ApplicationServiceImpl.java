package cn.iocoder.yudao.module.devops.service.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
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
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationEnvRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationEnvSaveReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationSaveReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationUpdateEnvsReqVO;
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
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;
import cn.iocoder.yudao.module.devops.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.environment.EnvironmentMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionVersionMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.ApprovalStatusEnum;
import cn.iocoder.yudao.module.devops.enums.ChangeEnvMountStatusEnum;
import cn.iocoder.yudao.module.devops.enums.ChangeStatusEnum;
import cn.iocoder.yudao.module.devops.enums.MergeStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineStatusEnum;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineSpecValidationService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionAsyncService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.PipelineApprovalContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.PipelineRunChangeSnapshotContext;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService;
import jakarta.annotation.Resource;
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
@Service
@Validated
public class ApplicationServiceImpl implements ApplicationService {

    private static final String CODE_MERGE_DISPLAY_NODE_ID = "builtin.code_merge";
    private static final String CODE_MERGE_DISPLAY_NODE_NAME = "代码合并";
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
    private static final String DEPLOY_BRANCH_PREFIX = "release/";
    private static final DateTimeFormatter DEPLOY_BRANCH_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

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
    private PipelineDefinitionMapper pipelineDefinitionMapper;
    @Resource
    private PipelineDefinitionVersionMapper pipelineDefinitionVersionMapper;
    @Resource
    private PipelineRunMapper pipelineRunMapper;
    @Resource
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Resource
    private PipelineSpecValidationService pipelineSpecValidationService;
    @Resource
    private RepositoryProviderService repositoryProviderService;
    @Resource
    private PipelineExecutionAsyncService pipelineExecutionAsyncService;

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
    public PageResult<ApplicationDO> getApplicationPage(ApplicationPageReqVO pageReqVO) {
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
        PipelineDefinitionDO pipelineDefinition = pipelineDefinitionMapper.selectByApplicationEnvId(applicationEnvId);
        ApplicationReleasePipelineRespVO pipeline = buildReleasePipeline(pipelineDefinition);
        PipelineRunDO run = getCurrentReleasePipelineRun(applicationEnvId);
        PipelineRunLogDO codeMergeLog = run == null ? null : pipelineRunLogMapper.selectByPipelineRunIdAndNodeType(
                run.getId(), PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE);
        Map<String, PipelineRunLogDO> runLogMap = run == null ? Collections.emptyMap()
                : pipelineRunLogMapper.selectListByPipelineRunId(run.getId()).stream()
                .filter(log -> log.getParentId() == null)
                .collect(Collectors.toMap(PipelineRunLogDO::getNodeId, Function.identity(), (first, second) -> first));

        ApplicationReleaseCurrentRunRespVO respVO = new ApplicationReleaseCurrentRunRespVO();
        respVO.setApplicationEnvId(applicationEnvId);
        respVO.setHasRun(run != null);
        respVO.setPolling(run != null && isRunPolling(run));
        respVO.setChangeSnapshots(run == null ? Collections.emptyList() : parseRunChangeSnapshots(run));
        respVO.setMountedBranches(buildReleaseBranches(applicationEnv).getMountedBranches());
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
        respVO.setNodes(buildCurrentRunNodes(pipeline.getNodes(), run, codeMergeLog, runLogMap));
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
        validateNoActivePipelineRun(applicationEnv.getId());

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
        String deployBranch = resolveDeployBranch(applicationEnv, unmountedChangeIds, orderedTargetChanges, now);
        PipelineRunDO pipelineRun = buildPipelineRun(pipelineDefinition, publishedVersion, applicationEnv,
                targetChangeEnvs, orderedTargetChanges, deployBranch, userId, now);
        pipelineRunMapper.insert(pipelineRun);

        for (ChangeEnvDO changeEnv : targetChangeEnvs) {
            fillMountedForPipelineRun(changeEnv, userId, now);
            changeEnv.setLastPipelineRunId(pipelineRun.getId());
            changeEnvMapper.updateById(changeEnv);
        }

        ApplicationReleaseSubmitBranchRespVO respVO = new ApplicationReleaseSubmitBranchRespVO();
        respVO.setApplicationEnvId(applicationEnv.getId());
        respVO.setMountedChangeIds(new ArrayList<>(targetChangeIds));
        respVO.setUnmountedChangeIds(unmountedChangeIds);
        respVO.setPipelineRunId(pipelineRun.getId());
        respVO.setRunStatus(pipelineRun.getRunStatus());
        // 异步开启整条流水线责任链：代码合并是责任链第一环，合并成功后由引擎驱动后续节点
        schedulePipelineStart(pipelineRun.getId(), new ArrayList<>(targetChangeIds), userId);
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
        List<PipelineRunDO> activeRuns = pipelineRunMapper.selectListByApplicationEnvIdAndStatuses(applicationEnvId,
                List.of(PipelineRunStatusEnum.QUEUED.getStatus(), PipelineRunStatusEnum.RUNNING.getStatus()));
        if (CollUtil.isNotEmpty(activeRuns)) {
            throw exception(PIPELINE_RUN_ACTIVE_EXISTS);
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
        respVO.setEnvStage(environment.getEnvStage());
        respVO.setInfraType(environment.getInfraType());
        respVO.setDisplayOrder(applicationEnv.getDisplayOrder());
        respVO.setPipelineDefinitionId(pipelineDefinition != null ? pipelineDefinition.getId()
                : applicationEnv.getPipelineDefinitionId());
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
        List<PipelineSpec.ExecutableStep> executableSteps = pipelineSpecValidationService.sortExecutableSteps(spec);
        if (spec == null || CollUtil.isEmpty(executableSteps)) {
            respVO.setEmptyReason(ApplicationReleasePipelineRespVO.EMPTY_REASON_SPEC_INVALID);
            return respVO;
        }
        respVO.setNodes(buildReleasePipelineNodes(executableSteps));
        respVO.setEdges(buildReleasePipelineEdges(executableSteps));
        return respVO;
    }

    private List<ApplicationReleasePipelineNodeRespVO> buildReleasePipelineNodes(
            List<PipelineSpec.ExecutableStep> steps) {
        List<ApplicationReleasePipelineNodeRespVO> result = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            PipelineSpec.ExecutableStep step = steps.get(i);
            ApplicationReleasePipelineNodeRespVO respVO = new ApplicationReleasePipelineNodeRespVO();
            respVO.setNodeId(step.getStepId());
            respVO.setType(step.getStep());
            respVO.setName(step.getName());
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
            List<PipelineSpec.ExecutableStep> steps) {
        List<ApplicationReleasePipelineEdgeRespVO> edges = new ArrayList<>();
        for (int i = 0; i + 1 < steps.size(); i++) {
            ApplicationReleasePipelineEdgeRespVO respVO = new ApplicationReleasePipelineEdgeRespVO();
            respVO.setSource(steps.get(i).getStepId());
            respVO.setTarget(steps.get(i + 1).getStepId());
            edges.add(respVO);
        }
        return edges;
    }

    private PipelineRunDO getCurrentReleasePipelineRun(Long applicationEnvId) {
        PipelineRunDO activeRun = pipelineRunMapper.selectLatestByApplicationEnvIdAndStatuses(applicationEnvId,
                List.of(PipelineRunStatusEnum.QUEUED.getStatus(), PipelineRunStatusEnum.RUNNING.getStatus()));
        return activeRun != null ? activeRun : pipelineRunMapper.selectLatestByApplicationEnvId(applicationEnvId);
    }

    private boolean isRunPolling(PipelineRunDO run) {
        return PipelineRunStatusEnum.QUEUED.getStatus().equals(run.getRunStatus())
                || PipelineRunStatusEnum.RUNNING.getStatus().equals(run.getRunStatus());
    }

    private List<ApplicationReleaseCurrentRunRespVO.Node> buildCurrentRunNodes(
            List<ApplicationReleasePipelineNodeRespVO> pipelineNodes, PipelineRunDO run, PipelineRunLogDO codeMergeLog,
            Map<String, PipelineRunLogDO> runLogMap) {
        List<ApplicationReleasePipelineNodeRespVO> sourceNodes = CollUtil.isEmpty(pipelineNodes)
                ? Collections.emptyList() : pipelineNodes;
        List<ApplicationReleaseCurrentRunRespVO.Node> nodes = new ArrayList<>(sourceNodes.size() + 1);
        boolean codeMergeMapped = false;
        for (ApplicationReleasePipelineNodeRespVO pipelineNode : sourceNodes) {
            ApplicationReleaseCurrentRunRespVO.Node node = buildPendingRunNode(pipelineNode);
            if (!codeMergeMapped && PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE.equals(pipelineNode.getType())) {
                applyCodeMergeLog(node, run, codeMergeLog);
                codeMergeMapped = true;
            } else {
                applyNodeRunLog(node, runLogMap.get(pipelineNode.getNodeId()));
            }
            nodes.add(node);
        }
        if (!codeMergeMapped && (run != null || codeMergeLog != null)) {
            nodes.add(0, buildBuiltinCodeMergeRunNode(run, codeMergeLog));
            for (int i = 0; i < nodes.size(); i++) {
                nodes.get(i).setDisplayOrder(i + 1);
            }
        }
        return nodes;
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
        node.setResult(JsonUtils.parseMap(runLog.getResultJson()));
        node.setHasDetail(true);
        node.setDetailType(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL.equals(runLog.getNodeType())
                ? DETAIL_TYPE_APPROVAL : DETAIL_TYPE_RUN_LOGS);
        fillNodeDisplay(node, runLog);
        if (PipelineNodeRegistryServiceImpl.TYPE_APPROVAL.equals(runLog.getNodeType())) {
            applyApprovalDetail(node, runLog);
        }
    }

    private ApplicationReleaseCurrentRunRespVO.Node buildPendingRunNode(ApplicationReleasePipelineNodeRespVO pipelineNode) {
        ApplicationReleaseCurrentRunRespVO.Node node = new ApplicationReleaseCurrentRunRespVO.Node();
        node.setNodeId(pipelineNode.getNodeId());
        node.setType(pipelineNode.getType());
        node.setName(pipelineNode.getName());
        node.setDisplayOrder(pipelineNode.getDisplayOrder());
        node.setEnabled(pipelineNode.getEnabled());
        node.setExecutionStatus(PipelineRunLogStatusEnum.PENDING.getStatus());
        fillNodeDisplay(node, null);
        node.setHasDetail(false);
        return node;
    }

    private ApplicationReleaseCurrentRunRespVO.Node buildBuiltinCodeMergeRunNode(PipelineRunDO run,
                                                                                 PipelineRunLogDO codeMergeLog) {
        ApplicationReleaseCurrentRunRespVO.Node node = new ApplicationReleaseCurrentRunRespVO.Node();
        node.setNodeId(CODE_MERGE_DISPLAY_NODE_ID);
        node.setType(PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE);
        node.setName(CODE_MERGE_DISPLAY_NODE_NAME);
        node.setDisplayOrder(1);
        node.setEnabled(true);
        node.setExecutionStatus(PipelineRunLogStatusEnum.PENDING.getStatus());
        fillNodeDisplay(node, null);
        node.setHasDetail(false);
        applyCodeMergeLog(node, run, codeMergeLog);
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
        node.setResult(JsonUtils.parseMap(codeMergeLog.getResultJson()));
        node.setHasDetail(true);
        boolean waitingInput = PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(codeMergeLog.getStatus());
        int conflictCount = waitingInput ? countConflicts(codeMergeLog.getContextJson()) : 0;
        node.setDetailType(waitingInput ? DETAIL_TYPE_CODE_MERGE : DETAIL_TYPE_RUN_LOGS);
        node.setConflictCount(conflictCount);
        node.setDetailRef(waitingInput ? buildCodeMergeDetailRef(node, codeMergeLog, conflictCount) : null);
        node.setActions(waitingInput ? List.of(buildResolveCodeConflictAction(node, codeMergeLog)) : Collections.emptyList());
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
                && StrUtil.isNotBlank(context.getProcessInstanceId())) {
            node.setActions(List.of(buildOpenApprovalDetailAction(context)));
        }
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

    private ApplicationReleaseCurrentRunRespVO.Action buildResolveCodeConflictAction(
            ApplicationReleaseCurrentRunRespVO.Node node, PipelineRunLogDO runLog) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("runId", runLog.getPipelineRunId());
        params.put("nodeId", StrUtil.blankToDefault(runLog.getNodeId(), node.getNodeId()));
        return buildRouteAction(ACTION_RESOLVE_CODE_CONFLICT, "解决冲突", "primary",
                "/devops/pipeline-run/" + runLog.getPipelineRunId() + "/code-merge/conflicts", params);
    }

    private ApplicationReleaseCurrentRunRespVO.Action buildOpenApprovalDetailAction(PipelineApprovalContext context) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("processInstanceId", context.getProcessInstanceId());
        return buildRouteAction(ACTION_OPEN_APPROVAL_DETAIL, "去审批", "primary",
                "/bpm/process-instance/detail", params);
    }

    private ApplicationReleaseCurrentRunRespVO.Action buildRouteAction(String code, String label, String style,
                                                                       String path, Map<String, Object> params) {
        ApplicationReleaseCurrentRunRespVO.Action action = new ApplicationReleaseCurrentRunRespVO.Action();
        action.setCode(code);
        action.setLabel(label);
        action.setStyle(style);
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
        pipelineRun.setTriggerType("APPLICATION_RELEASE_TAB");
        pipelineRun.setTriggerUserId(userId);
        pipelineRun.setTriggeredAt(now);
        pipelineRun.setStartedAt(now);
        return pipelineRun;
    }

    private String resolveDeployBranch(ApplicationEnvDO applicationEnv, List<Long> unmountedChangeIds,
                                       List<ChangeDO> targetChanges, LocalDateTime now) {
        if (CollUtil.isNotEmpty(targetChanges) && CollUtil.isEmpty(unmountedChangeIds)) {
            PipelineRunDO latestReleaseRun = pipelineRunMapper.selectLatestByApplicationEnvIdAndBranchPrefix(
                    applicationEnv.getId(), DEPLOY_BRANCH_PREFIX);
            if (latestReleaseRun != null) {
                return latestReleaseRun.getBranchName();
            }
        }
        EnvironmentDO environment = validateEnvironmentExists(applicationEnv.getEnvId());
        return DEPLOY_BRANCH_PREFIX + sanitizeRefPart(environment.getEnvKey()) + "/"
                + DEPLOY_BRANCH_TIMESTAMP_FORMATTER.format(now);
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

    private List<PipelineRunChangeSnapshotContext> buildRunChangeSnapshots(List<ChangeDO> changes) {
        return changes.stream()
                .map(change -> new PipelineRunChangeSnapshotContext(change.getId(), change.getLatestCommitSha()))
                .toList();
    }

    /**
     * 调度流水线执行：在事务提交后异步开启整条流水线责任链（代码合并为首环）。
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
