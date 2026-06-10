package cn.iocoder.yudao.module.devops.service.application;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseCurrentRunRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseEnvDetailRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseEnvTabRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseSubmitBranchReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseSubmitBranchRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationSaveReqVO;
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
import cn.iocoder.yudao.module.devops.enums.PipelineNodeTypeEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderTypeEnum;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineSpecValidationService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineCodeMergeAsyncService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.PipelineRunChangeSnapshotContext;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_REPO_IDENTIFIER_DUPLICATE;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_PUBLISHED_VERSION_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_RUN_ACTIVE_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationServiceImpl} 的单元测试。
 */
public class ApplicationServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ApplicationServiceImpl applicationService;

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private ApplicationEnvMapper applicationEnvMapper;
    @Mock
    private EnvironmentMapper environmentMapper;
    @Mock
    private ChangeMapper changeMapper;
    @Mock
    private ChangeEnvMapper changeEnvMapper;
    @Mock
    private PipelineDefinitionMapper pipelineDefinitionMapper;
    @Mock
    private PipelineDefinitionVersionMapper pipelineDefinitionVersionMapper;
    @Mock
    private PipelineRunMapper pipelineRunMapper;
    @Mock
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Mock
    private PipelineSpecValidationService pipelineSpecValidationService;
    @Mock
    private RepositoryProviderService repositoryProviderService;
    @Mock
    private PipelineExecutionService pipelineExecutionService;
    @Mock
    private PipelineCodeMergeAsyncService pipelineCodeMergeAsyncService;

    @Test
    public void testCreateApplication_repositoryProviderLinkage() {
        // 准备参数
        ApplicationSaveReqVO reqVO = buildSaveReqVO(10L, "group/gone-cloud");
        reqVO.setRepoProviderType("GITHUB");
        RepositoryProviderDO repositoryProvider = buildRepositoryProvider(10L,
                RepositoryProviderTypeEnum.GITLAB.getProviderType());
        when(repositoryProviderService.validateRepositoryProviderExists(eq(10L))).thenReturn(repositoryProvider);
        doAnswer(invocation -> {
            ApplicationDO application = invocation.getArgument(0);
            application.setId(100L);
            return 1;
        }).when(applicationMapper).insert(any(ApplicationDO.class));

        // 调用
        Long id = applicationService.createApplication(reqVO);

        // 断言
        assertEquals(100L, id);
        ArgumentCaptor<ApplicationDO> applicationCaptor = ArgumentCaptor.forClass(ApplicationDO.class);
        verify(applicationMapper).insert(applicationCaptor.capture());
        ApplicationDO application = applicationCaptor.getValue();
        assertEquals(10L, application.getRepositoryProviderId());
        assertEquals(RepositoryProviderTypeEnum.GITLAB.getProviderType(), application.getRepoProviderType());
        assertEquals("group/gone-cloud", application.getRepoIdentifier());
        verify(applicationMapper).selectByRepositoryProviderIdAndRepoIdentifier(eq(10L), eq("group/gone-cloud"));
    }

    @Test
    public void testCreateApplication_duplicateRepositoryInSameProvider() {
        // 准备参数
        ApplicationSaveReqVO reqVO = buildSaveReqVO(10L, "group/gone-cloud");
        when(repositoryProviderService.validateRepositoryProviderExists(eq(10L)))
                .thenReturn(buildRepositoryProvider(10L, RepositoryProviderTypeEnum.GITLAB.getProviderType()));
        ApplicationDO existApplication = new ApplicationDO();
        existApplication.setId(99L);
        when(applicationMapper.selectByRepositoryProviderIdAndRepoIdentifier(eq(10L), eq("group/gone-cloud")))
                .thenReturn(existApplication);

        // 调用并断言
        assertServiceException(() -> applicationService.createApplication(reqVO), APPLICATION_REPO_IDENTIFIER_DUPLICATE);
    }

    @Test
    public void testUpdateApplication_repositoryProviderLinkage() {
        // 准备参数
        ApplicationSaveReqVO reqVO = buildSaveReqVO(20L, "group/gone-cloud");
        reqVO.setId(100L);
        reqVO.setRepoProviderType("GITHUB");
        ApplicationDO oldApplication = new ApplicationDO();
        oldApplication.setId(100L);
        when(applicationMapper.selectById(eq(100L))).thenReturn(oldApplication);
        when(repositoryProviderService.validateRepositoryProviderExists(eq(20L)))
                .thenReturn(buildRepositoryProvider(20L, RepositoryProviderTypeEnum.GITLAB.getProviderType()));

        // 调用
        applicationService.updateApplication(reqVO);

        // 断言
        ArgumentCaptor<ApplicationDO> applicationCaptor = ArgumentCaptor.forClass(ApplicationDO.class);
        verify(applicationMapper).updateById(applicationCaptor.capture());
        ApplicationDO application = applicationCaptor.getValue();
        assertEquals(100L, application.getId());
        assertEquals(20L, application.getRepositoryProviderId());
        assertEquals(RepositoryProviderTypeEnum.GITLAB.getProviderType(), application.getRepoProviderType());
    }

    @Test
    public void testGetApplicationReleaseEnvTabs() {
        // 准备参数
        ApplicationDO application = new ApplicationDO();
        application.setId(1L);
        when(applicationMapper.selectById(eq(1L))).thenReturn(application);

        ApplicationEnvDO testApplicationEnv = buildApplicationEnv(100L, 1L, 10L, 20, 200L);
        ApplicationEnvDO prodApplicationEnv = buildApplicationEnv(101L, 1L, 11L, 30, null);
        when(applicationEnvMapper.selectListByAppIdOrderByDisplayOrder(eq(1L)))
                .thenReturn(List.of(testApplicationEnv, prodApplicationEnv));
        when(environmentMapper.selectListByIds(any()))
                .thenReturn(List.of(buildEnvironment(10L, "test", "测试环境"),
                        buildEnvironment(11L, "prod", "生产环境")));

        PipelineDefinitionDO definition = buildPipelineDefinition(200L, 100L, 300L);
        when(pipelineDefinitionMapper.selectListByApplicationEnvIds(any()))
                .thenReturn(List.of(definition));

        // 调用
        List<ApplicationReleaseEnvTabRespVO> tabs = applicationService.getApplicationReleaseEnvTabs(1L);

        // 断言
        assertEquals(2, tabs.size());
        assertEquals(100L, tabs.get(0).getApplicationEnvId());
        assertEquals("test", tabs.get(0).getEnvKey());
        assertEquals("测试环境", tabs.get(0).getEnvName());
        assertEquals(200L, tabs.get(0).getPipelineDefinitionId());
        assertEquals(true, tabs.get(0).getHasPublishedPipeline());
        assertEquals(101L, tabs.get(1).getApplicationEnvId());
        assertEquals("prod", tabs.get(1).getEnvKey());
        assertFalse(tabs.get(1).getHasPublishedPipeline());
    }

    @Test
    public void testGetApplicationReleaseEnvDetail() {
        // 准备参数
        ApplicationEnvDO applicationEnv = buildApplicationEnv(100L, 1L, 10L, 20, 200L);
        when(applicationEnvMapper.selectById(eq(100L))).thenReturn(applicationEnv);
        when(environmentMapper.selectById(eq(10L))).thenReturn(buildEnvironment(10L, "test", "测试环境"));

        PipelineDefinitionDO definition = buildPipelineDefinition(200L, 100L, 300L);
        when(pipelineDefinitionMapper.selectByApplicationEnvId(eq(100L))).thenReturn(definition);
        PipelineDefinitionVersionDO version = buildPipelineDefinitionVersion(300L, 200L);
        when(pipelineDefinitionVersionMapper.selectById(eq(300L))).thenReturn(version);
        PipelineSpec spec = buildPipelineSpec();
        when(pipelineSpecValidationService.parseSpec(eq(version.getSpecJson()), any())).thenReturn(spec);
        when(pipelineSpecValidationService.sortNodes(eq(spec))).thenReturn(spec.getNodes());

        ChangeDO mountedChange = buildChange(11L, "feat/login-1", LocalDateTime.of(2026, 6, 7, 10, 0));
        ChangeDO unmountedChange = buildChange(12L, "feat/report-1", LocalDateTime.of(2026, 6, 7, 9, 0));
        ChangeDO neverMountedChange = buildChange(13L, "feat/order-1", LocalDateTime.of(2026, 6, 7, 8, 0));
        when(changeMapper.selectListByAppIdAndStatus(eq(1L), eq(ChangeStatusEnum.ACTIVE.getStatus())))
                .thenReturn(List.of(mountedChange, unmountedChange, neverMountedChange));
        when(changeEnvMapper.selectListByApplicationEnvId(eq(100L))).thenReturn(List.of(
                buildChangeEnv(900L, 11L, ChangeEnvMountStatusEnum.MOUNTED.getStatus()),
                buildChangeEnv(901L, 12L, ChangeEnvMountStatusEnum.UNMOUNTED.getStatus())));

        // 调用
        ApplicationReleaseEnvDetailRespVO detail = applicationService.getApplicationReleaseEnvDetail(100L);

        // 断言
        assertEquals(100L, detail.getEnv().getApplicationEnvId());
        assertEquals(200L, detail.getPipeline().getDefinitionId());
        assertEquals(300L, detail.getPipeline().getPublishedVersionId());
        assertNull(detail.getPipeline().getEmptyReason());
        assertEquals(3, detail.getPipeline().getNodes().size());
        assertEquals("checkout", detail.getPipeline().getNodes().get(0).getNodeId());
        assertEquals(1, detail.getPipeline().getNodes().get(0).getDisplayOrder());
        assertEquals("unit_test", detail.getPipeline().getEdges().get(0).getTarget());

        assertEquals(1, detail.getMountedBranches().size());
        assertEquals(11L, detail.getMountedBranches().get(0).getChangeId());
        assertEquals(900L, detail.getMountedBranches().get(0).getChangeEnvId());
        assertEquals(PipelineStatusEnum.SUCCESS.getStatus(), detail.getMountedBranches().get(0).getLastBuildStatus());

        assertEquals(2, detail.getUnmountedBranches().size());
        assertEquals(12L, detail.getUnmountedBranches().get(0).getChangeId());
        assertEquals(901L, detail.getUnmountedBranches().get(0).getChangeEnvId());
        assertEquals(13L, detail.getUnmountedBranches().get(1).getChangeId());
        assertNull(detail.getUnmountedBranches().get(1).getChangeEnvId());
    }

    @Test
    public void testGetApplicationReleaseEnvDetail_noPublishedPipeline() {
        // 准备参数
        ApplicationEnvDO applicationEnv = buildApplicationEnv(100L, 1L, 10L, 20, 200L);
        when(applicationEnvMapper.selectById(eq(100L))).thenReturn(applicationEnv);
        when(environmentMapper.selectById(eq(10L))).thenReturn(buildEnvironment(10L, "test", "测试环境"));
        when(pipelineDefinitionMapper.selectByApplicationEnvId(eq(100L)))
                .thenReturn(buildPipelineDefinition(200L, 100L, null));
        when(changeMapper.selectListByAppIdAndStatus(eq(1L), eq(ChangeStatusEnum.ACTIVE.getStatus())))
                .thenReturn(List.of());

        // 调用
        ApplicationReleaseEnvDetailRespVO detail = applicationService.getApplicationReleaseEnvDetail(100L);

        // 断言
        assertEquals(200L, detail.getPipeline().getDefinitionId());
        assertEquals("NO_PUBLISHED_VERSION", detail.getPipeline().getEmptyReason());
        assertEquals(0, detail.getPipeline().getNodes().size());
        assertEquals(0, detail.getMountedBranches().size());
        assertEquals(0, detail.getUnmountedBranches().size());
    }

    @Test
    public void testGetApplicationReleaseCurrentRun_cacheable() throws Exception {
        // 调用
        Method method = ApplicationServiceImpl.class.getMethod("getApplicationReleaseCurrentRun", Long.class);
        Cacheable cacheable = method.getAnnotation(Cacheable.class);

        // 断言
        assertEquals(RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN, cacheable.value()[0]);
        assertEquals("#applicationEnvId", cacheable.key());
        assertEquals("#result == null", cacheable.unless());
    }

    @Test
    public void testGetApplicationReleaseCurrentRun_withoutRun() {
        // 准备参数
        ApplicationEnvDO applicationEnv = buildApplicationEnv(100L, 1L, 10L, 20, 200L);
        when(applicationEnvMapper.selectById(eq(100L))).thenReturn(applicationEnv);
        PipelineDefinitionDO definition = buildPipelineDefinition(200L, 100L, 300L);
        when(pipelineDefinitionMapper.selectByApplicationEnvId(eq(100L))).thenReturn(definition);
        PipelineDefinitionVersionDO version = buildPipelineDefinitionVersion(300L, 200L);
        when(pipelineDefinitionVersionMapper.selectById(eq(300L))).thenReturn(version);
        PipelineSpec spec = buildPipelineSpec();
        when(pipelineSpecValidationService.parseSpec(eq(version.getSpecJson()), any())).thenReturn(spec);
        when(pipelineSpecValidationService.sortNodes(eq(spec))).thenReturn(spec.getNodes());
        when(pipelineRunMapper.selectLatestByApplicationEnvIdAndStatuses(eq(100L), any())).thenReturn(null);
        when(pipelineRunMapper.selectLatestByApplicationEnvId(eq(100L))).thenReturn(null);
        ChangeDO mountedChange = buildChange(11L, "feat/login-1", LocalDateTime.of(2026, 6, 7, 10, 0));
        ChangeDO unmountedChange = buildChange(12L, "feat/report-1", LocalDateTime.of(2026, 6, 7, 9, 0));
        when(changeMapper.selectListByAppIdAndStatus(eq(1L), eq(ChangeStatusEnum.ACTIVE.getStatus())))
                .thenReturn(List.of(mountedChange, unmountedChange));
        when(changeEnvMapper.selectListByApplicationEnvId(eq(100L))).thenReturn(List.of(
                buildChangeEnv(900L, 11L, ChangeEnvMountStatusEnum.MOUNTED.getStatus()),
                buildChangeEnv(901L, 12L, ChangeEnvMountStatusEnum.UNMOUNTED.getStatus())));

        // 调用
        ApplicationReleaseCurrentRunRespVO respVO = applicationService.getApplicationReleaseCurrentRun(100L);

        // 断言
        assertFalse(respVO.getHasRun());
        assertFalse(respVO.getPolling());
        assertEquals(3, respVO.getNodes().size());
        assertEquals("checkout", respVO.getNodes().get(0).getNodeId());
        assertEquals(PipelineRunLogStatusEnum.PENDING.getStatus(), respVO.getNodes().get(0).getExecutionStatus());
        assertEquals(1, respVO.getMountedBranches().size());
        assertEquals(11L, respVO.getMountedBranches().get(0).getChangeId());
        assertEquals(900L, respVO.getMountedBranches().get(0).getChangeEnvId());
        assertEquals(PipelineStatusEnum.SUCCESS.getStatus(), respVO.getMountedBranches().get(0).getLastBuildStatus());
    }

    @Test
    public void testGetApplicationReleaseCurrentRun_waitingInput() {
        // 准备参数
        ApplicationEnvDO applicationEnv = buildApplicationEnv(100L, 1L, 10L, 20, 200L);
        when(applicationEnvMapper.selectById(eq(100L))).thenReturn(applicationEnv);
        PipelineDefinitionDO definition = buildPipelineDefinition(200L, 100L, 300L);
        when(pipelineDefinitionMapper.selectByApplicationEnvId(eq(100L))).thenReturn(definition);
        PipelineDefinitionVersionDO version = buildPipelineDefinitionVersion(300L, 200L);
        when(pipelineDefinitionVersionMapper.selectById(eq(300L))).thenReturn(version);
        PipelineSpec spec = buildPipelineSpec();
        when(pipelineSpecValidationService.parseSpec(eq(version.getSpecJson()), any())).thenReturn(spec);
        when(pipelineSpecValidationService.sortNodes(eq(spec))).thenReturn(spec.getNodes());
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setApplicationEnvId(100L);
        run.setRunStatus(PipelineRunStatusEnum.RUNNING.getStatus());
        run.setTriggerType("APPLICATION_RELEASE_TAB");
        run.setChangeSnapshotJson(JsonUtils.toJsonString(List.of(
                new PipelineRunChangeSnapshotContext(11L, "sha-11"),
                new PipelineRunChangeSnapshotContext(12L, "sha-12"))));
        when(pipelineRunMapper.selectLatestByApplicationEnvIdAndStatuses(eq(100L), any())).thenReturn(run);
        PipelineRunLogDO log = new PipelineRunLogDO();
        log.setId(900L);
        log.setPipelineRunId(800L);
        log.setNodeType(PipelineNodeTypeEnum.CODE_MERGE.getType());
        log.setStatus(PipelineRunLogStatusEnum.WAITING_INPUT.getStatus());
        log.setSummary("代码合并冲突：feat/login-1");
        log.setContextJson(JsonUtils.toJsonString(Map.of("conflicts", List.of(
                Map.of("filePath", "src/App.java"),
                Map.of("filePath", "src/User.java")
        ))));
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeType(eq(800L), eq(PipelineNodeTypeEnum.CODE_MERGE.getType())))
                .thenReturn(log);

        // 调用
        ApplicationReleaseCurrentRunRespVO respVO = applicationService.getApplicationReleaseCurrentRun(100L);

        // 断言
        assertEquals(true, respVO.getHasRun());
        assertEquals(true, respVO.getPolling());
        assertEquals(800L, respVO.getPipelineRunId());
        assertEquals(2, respVO.getChangeSnapshots().size());
        assertEquals(11L, respVO.getChangeSnapshots().get(0).getChangeId());
        assertEquals("sha-11", respVO.getChangeSnapshots().get(0).getCommitSha());
        assertEquals(12L, respVO.getChangeSnapshots().get(1).getChangeId());
        assertEquals("sha-12", respVO.getChangeSnapshots().get(1).getCommitSha());
        assertEquals(PipelineRunLogStatusEnum.WAITING_INPUT.getStatus(),
                respVO.getNodes().get(0).getExecutionStatus());
        assertEquals("CODE_MERGE_CONFLICT", respVO.getNodes().get(0).getDetailType());
        assertEquals(2, respVO.getNodes().get(0).getConflictCount());
        assertEquals(true, respVO.getNodes().get(0).getHasDetail());
    }

    @Test
    public void testSubmitApplicationReleaseBranch_evictCurrentRunCache() throws Exception {
        // 调用
        Method method = ApplicationServiceImpl.class.getMethod("submitApplicationReleaseBranch",
                ApplicationReleaseSubmitBranchReqVO.class, Long.class);
        CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);

        // 断言
        assertEquals(RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN, cacheEvict.value()[0]);
        assertEquals("#reqVO.applicationEnvId", cacheEvict.key());
    }

    @Test
    public void testSubmitApplicationReleaseBranch_syncTargetSetAddChanges() {
        // 准备参数
        ChangeDO changeA = buildChange(11L, "feat/login-1", LocalDateTime.of(2026, 6, 7, 10, 0));
        ChangeDO changeB = buildChange(12L, "feat/report-1", LocalDateTime.of(2026, 6, 7, 9, 0));
        ChangeDO changeC = buildChange(13L, "feat/order-1", LocalDateTime.of(2026, 6, 7, 8, 0));
        ChangeDO changeD = buildChange(14L, "feat/pay-1", LocalDateTime.of(2026, 6, 7, 7, 0));
        ChangeDO changeE = buildChange(15L, "feat/member-1", LocalDateTime.of(2026, 6, 7, 6, 0));
        when(changeMapper.selectListByIds(any())).thenReturn(List.of(changeA, changeB, changeC, changeD, changeE));
        ApplicationEnvDO applicationEnv = buildApplicationEnv(100L, 1L, 10L, 20, 200L);
        when(applicationEnvMapper.selectById(eq(100L))).thenReturn(applicationEnv);
        PipelineDefinitionDO definition = buildPipelineDefinition(200L, 100L, 300L);
        when(pipelineDefinitionMapper.selectByApplicationEnvId(eq(100L))).thenReturn(definition);
        PipelineDefinitionVersionDO version = buildPipelineDefinitionVersion(300L, 200L);
        when(pipelineDefinitionVersionMapper.selectById(eq(300L))).thenReturn(version);
        PipelineRunDO latestReleaseRun = new PipelineRunDO();
        latestReleaseRun.setId(700L);
        latestReleaseRun.setRunStatus(PipelineRunStatusEnum.FAILED.getStatus());
        latestReleaseRun.setBranchName("release/test/20260607120000");
        when(pipelineRunMapper.selectLatestByApplicationEnvIdAndBranchPrefix(eq(100L), eq("release/")))
                .thenReturn(latestReleaseRun);
        when(changeEnvMapper.selectListByApplicationEnvId(eq(100L))).thenReturn(List.of(
                buildChangeEnv(911L, 11L, ChangeEnvMountStatusEnum.MOUNTED.getStatus()),
                buildChangeEnv(912L, 12L, ChangeEnvMountStatusEnum.MOUNTED.getStatus()),
                buildChangeEnv(913L, 13L, ChangeEnvMountStatusEnum.MOUNTED.getStatus())));
        doAnswer(invocation -> {
            ChangeEnvDO changeEnv = invocation.getArgument(0);
            changeEnv.setId(900L + changeEnv.getChangeId());
            return 1;
        }).when(changeEnvMapper).insert(any(ChangeEnvDO.class));
        doAnswer(invocation -> {
            PipelineRunDO pipelineRun = invocation.getArgument(0);
            pipelineRun.setId(800L);
            return 1;
        }).when(pipelineRunMapper).insert(any(PipelineRunDO.class));
        ApplicationReleaseSubmitBranchReqVO reqVO = new ApplicationReleaseSubmitBranchReqVO();
        reqVO.setApplicationEnvId(100L);
        reqVO.setChangeIds(List.of(11L, 12L, 13L, 14L, 15L));

        // 调用
        ApplicationReleaseSubmitBranchRespVO respVO = applicationService.submitApplicationReleaseBranch(reqVO, 99L);

        // 断言
        assertEquals(100L, respVO.getApplicationEnvId());
        assertEquals(List.of(11L, 12L, 13L, 14L, 15L), respVO.getMountedChangeIds());
        assertEquals(List.of(), respVO.getUnmountedChangeIds());
        assertEquals(800L, respVO.getPipelineRunId());
        assertEquals(PipelineRunStatusEnum.RUNNING.getStatus(), respVO.getRunStatus());

        ArgumentCaptor<PipelineRunDO> pipelineRunCaptor = ArgumentCaptor.forClass(PipelineRunDO.class);
        verify(pipelineRunMapper).insert(pipelineRunCaptor.capture());
        PipelineRunDO pipelineRun = pipelineRunCaptor.getValue();
        assertEquals(200L, pipelineRun.getDefinitionId());
        assertEquals(300L, pipelineRun.getDefinitionVersionId());
        assertEquals(1L, pipelineRun.getAppId());
        assertEquals(100L, pipelineRun.getApplicationEnvId());
        assertEquals(11L, pipelineRun.getChangeId());
        assertEquals(911L, pipelineRun.getChangeEnvId());
        assertEquals("release/test/20260607120000", pipelineRun.getBranchName());
        assertEquals("sha-11", pipelineRun.getCommitSha());
        List<PipelineRunChangeSnapshotContext> snapshots = JsonUtils.parseArray(pipelineRun.getChangeSnapshotJson(),
                PipelineRunChangeSnapshotContext.class);
        assertEquals(5, snapshots.size());
        assertEquals(11L, snapshots.get(0).getChangeId());
        assertEquals("sha-11", snapshots.get(0).getCommitSha());
        assertEquals(15L, snapshots.get(4).getChangeId());
        assertEquals("sha-15", snapshots.get(4).getCommitSha());
        assertEquals("APPLICATION_RELEASE_TAB", pipelineRun.getTriggerType());
        assertEquals(99L, pipelineRun.getTriggerUserId());

        ArgumentCaptor<ChangeEnvDO> changeEnvInsertCaptor = ArgumentCaptor.forClass(ChangeEnvDO.class);
        verify(changeEnvMapper, times(2)).insert(changeEnvInsertCaptor.capture());
        assertEquals(List.of(14L, 15L), changeEnvInsertCaptor.getAllValues().stream()
                .map(ChangeEnvDO::getChangeId).toList());

        ArgumentCaptor<ChangeEnvDO> changeEnvUpdateCaptor = ArgumentCaptor.forClass(ChangeEnvDO.class);
        verify(changeEnvMapper, times(5)).updateById(changeEnvUpdateCaptor.capture());
        assertEquals(List.of(11L, 12L, 13L, 14L, 15L), changeEnvUpdateCaptor.getAllValues().stream()
                .map(ChangeEnvDO::getChangeId).toList());
        changeEnvUpdateCaptor.getAllValues().forEach(changeEnv -> {
            assertEquals(800L, changeEnv.getLastPipelineRunId());
            assertEquals(ChangeEnvMountStatusEnum.MOUNTED.getStatus(), changeEnv.getMountStatus());
            assertEquals(PipelineStatusEnum.PENDING.getStatus(), changeEnv.getLastBuildStatus());
        });
        verify(pipelineCodeMergeAsyncService).startCodeMergeAsync(eq(800L), eq(List.of(11L, 12L, 13L, 14L, 15L)), eq(99L));
    }

    @Test
    public void testSubmitApplicationReleaseBranch_syncTargetSetRemoveChanges() {
        // 准备参数
        ChangeDO changeA = buildChange(11L, "feat/login-1", LocalDateTime.of(2026, 6, 7, 10, 0));
        when(changeMapper.selectListByIds(any())).thenReturn(List.of(changeA));
        ApplicationEnvDO applicationEnv = buildApplicationEnv(100L, 1L, 10L, 20, 200L);
        when(applicationEnvMapper.selectById(eq(100L))).thenReturn(applicationEnv);
        PipelineDefinitionDO definition = buildPipelineDefinition(200L, 100L, 300L);
        when(pipelineDefinitionMapper.selectByApplicationEnvId(eq(100L))).thenReturn(definition);
        PipelineDefinitionVersionDO version = buildPipelineDefinitionVersion(300L, 200L);
        when(pipelineDefinitionVersionMapper.selectById(eq(300L))).thenReturn(version);
        when(environmentMapper.selectById(eq(10L))).thenReturn(buildEnvironment(10L, "test", "测试环境"));
        when(changeEnvMapper.selectListByApplicationEnvId(eq(100L))).thenReturn(List.of(
                buildChangeEnv(911L, 11L, ChangeEnvMountStatusEnum.MOUNTED.getStatus()),
                buildChangeEnv(912L, 12L, ChangeEnvMountStatusEnum.MOUNTED.getStatus()),
                buildChangeEnv(913L, 13L, ChangeEnvMountStatusEnum.MOUNTED.getStatus())));
        doAnswer(invocation -> {
            PipelineRunDO pipelineRun = invocation.getArgument(0);
            pipelineRun.setId(800L);
            return 1;
        }).when(pipelineRunMapper).insert(any(PipelineRunDO.class));
        ApplicationReleaseSubmitBranchReqVO reqVO = new ApplicationReleaseSubmitBranchReqVO();
        reqVO.setApplicationEnvId(100L);
        reqVO.setChangeIds(List.of(11L));

        // 调用
        ApplicationReleaseSubmitBranchRespVO respVO = applicationService.submitApplicationReleaseBranch(reqVO, 99L);

        // 断言
        assertEquals(100L, respVO.getApplicationEnvId());
        assertEquals(List.of(11L), respVO.getMountedChangeIds());
        assertEquals(List.of(12L, 13L), respVO.getUnmountedChangeIds());
        assertEquals(800L, respVO.getPipelineRunId());
        assertEquals(PipelineRunStatusEnum.RUNNING.getStatus(), respVO.getRunStatus());

        ArgumentCaptor<PipelineRunDO> pipelineRunCaptor = ArgumentCaptor.forClass(PipelineRunDO.class);
        verify(pipelineRunMapper).insert(pipelineRunCaptor.capture());
        assertTrue(pipelineRunCaptor.getValue().getBranchName().matches("release/test/\\d{14}"));

        ArgumentCaptor<ChangeEnvDO> changeEnvUpdateCaptor = ArgumentCaptor.forClass(ChangeEnvDO.class);
        verify(changeEnvMapper, times(3)).updateById(changeEnvUpdateCaptor.capture());
        ChangeEnvDO unmountedB = changeEnvUpdateCaptor.getAllValues().get(0);
        ChangeEnvDO unmountedC = changeEnvUpdateCaptor.getAllValues().get(1);
        ChangeEnvDO mountedA = changeEnvUpdateCaptor.getAllValues().get(2);
        assertEquals(12L, unmountedB.getChangeId());
        assertEquals(ChangeEnvMountStatusEnum.UNMOUNTED.getStatus(), unmountedB.getMountStatus());
        assertEquals("RELEASE_TARGET_SET_SYNC", unmountedB.getUnmountedReason());
        assertEquals(13L, unmountedC.getChangeId());
        assertEquals(ChangeEnvMountStatusEnum.UNMOUNTED.getStatus(), unmountedC.getMountStatus());
        assertEquals(11L, mountedA.getChangeId());
        assertEquals(800L, mountedA.getLastPipelineRunId());
        assertEquals(PipelineStatusEnum.PENDING.getStatus(), mountedA.getLastBuildStatus());
        verify(pipelineCodeMergeAsyncService).startCodeMergeAsync(eq(800L), eq(List.of(11L)), eq(99L));
    }

    @Test
    public void testSubmitApplicationReleaseBranch_emptyTargetSetUnmountAllAndReleaseBaseBranch() {
        // 准备参数
        ApplicationEnvDO applicationEnv = buildApplicationEnv(100L, 1L, 10L, 20, 200L);
        when(applicationEnvMapper.selectById(eq(100L))).thenReturn(applicationEnv);
        PipelineDefinitionDO definition = buildPipelineDefinition(200L, 100L, 300L);
        when(pipelineDefinitionMapper.selectByApplicationEnvId(eq(100L))).thenReturn(definition);
        PipelineDefinitionVersionDO version = buildPipelineDefinitionVersion(300L, 200L);
        when(pipelineDefinitionVersionMapper.selectById(eq(300L))).thenReturn(version);
        when(environmentMapper.selectById(eq(10L))).thenReturn(buildEnvironment(10L, "test", "测试环境"));
        when(changeEnvMapper.selectListByApplicationEnvId(eq(100L))).thenReturn(List.of(
                buildChangeEnv(911L, 11L, ChangeEnvMountStatusEnum.MOUNTED.getStatus()),
                buildChangeEnv(912L, 12L, ChangeEnvMountStatusEnum.MOUNTED.getStatus()),
                buildChangeEnv(913L, 13L, ChangeEnvMountStatusEnum.MOUNTED.getStatus())));
        doAnswer(invocation -> {
            PipelineRunDO pipelineRun = invocation.getArgument(0);
            pipelineRun.setId(800L);
            return 1;
        }).when(pipelineRunMapper).insert(any(PipelineRunDO.class));
        ApplicationReleaseSubmitBranchReqVO reqVO = new ApplicationReleaseSubmitBranchReqVO();
        reqVO.setApplicationEnvId(100L);
        reqVO.setChangeIds(List.of());

        // 调用
        ApplicationReleaseSubmitBranchRespVO respVO = applicationService.submitApplicationReleaseBranch(reqVO, 99L);

        // 断言
        assertEquals(100L, respVO.getApplicationEnvId());
        assertEquals(List.of(), respVO.getMountedChangeIds());
        assertEquals(List.of(11L, 12L, 13L), respVO.getUnmountedChangeIds());
        assertEquals(800L, respVO.getPipelineRunId());
        assertEquals(PipelineRunStatusEnum.RUNNING.getStatus(), respVO.getRunStatus());
        verify(changeMapper, never()).selectListByIds(any());

        ArgumentCaptor<PipelineRunDO> pipelineRunCaptor = ArgumentCaptor.forClass(PipelineRunDO.class);
        verify(pipelineRunMapper).insert(pipelineRunCaptor.capture());
        PipelineRunDO pipelineRun = pipelineRunCaptor.getValue();
        assertEquals(200L, pipelineRun.getDefinitionId());
        assertEquals(300L, pipelineRun.getDefinitionVersionId());
        assertEquals(1L, pipelineRun.getAppId());
        assertEquals(100L, pipelineRun.getApplicationEnvId());
        assertNull(pipelineRun.getChangeId());
        assertNull(pipelineRun.getChangeEnvId());
        assertNull(pipelineRun.getCommitSha());
        assertTrue(pipelineRun.getBranchName().matches("release/test/\\d{14}"));
        assertEquals(List.of(), JsonUtils.parseArray(pipelineRun.getChangeSnapshotJson(),
                PipelineRunChangeSnapshotContext.class));
        verify(pipelineCodeMergeAsyncService).startCodeMergeAsync(eq(800L), eq(List.of()), eq(99L));
        verify(changeEnvMapper, times(3)).updateById(any(ChangeEnvDO.class));
    }

    @Test
    public void testSubmitApplicationReleaseBranch_startCodeMergeAfterCommitAsync() {
        // 准备参数
        ChangeDO change = buildChange(11L, "feat/login-1", LocalDateTime.of(2026, 6, 7, 10, 0));
        when(changeMapper.selectListByIds(any())).thenReturn(List.of(change));
        ApplicationEnvDO applicationEnv = buildApplicationEnv(100L, 1L, 10L, 20, 200L);
        when(applicationEnvMapper.selectById(eq(100L))).thenReturn(applicationEnv);
        PipelineDefinitionDO definition = buildPipelineDefinition(200L, 100L, 300L);
        when(pipelineDefinitionMapper.selectByApplicationEnvId(eq(100L))).thenReturn(definition);
        PipelineDefinitionVersionDO version = buildPipelineDefinitionVersion(300L, 200L);
        when(pipelineDefinitionVersionMapper.selectById(eq(300L))).thenReturn(version);
        when(environmentMapper.selectById(eq(10L))).thenReturn(buildEnvironment(10L, "test", "测试环境"));
        when(changeEnvMapper.selectListByApplicationEnvId(eq(100L))).thenReturn(List.of(
                buildChangeEnv(911L, 11L, ChangeEnvMountStatusEnum.MOUNTED.getStatus())));
        doAnswer(invocation -> {
            PipelineRunDO pipelineRun = invocation.getArgument(0);
            pipelineRun.setId(800L);
            return 1;
        }).when(pipelineRunMapper).insert(any(PipelineRunDO.class));
        ApplicationReleaseSubmitBranchReqVO reqVO = new ApplicationReleaseSubmitBranchReqVO();
        reqVO.setApplicationEnvId(100L);
        reqVO.setChangeIds(List.of(11L));

        // 调用
        TransactionSynchronizationManager.initSynchronization();
        try {
            applicationService.submitApplicationReleaseBranch(reqVO, 99L);
            verify(pipelineCodeMergeAsyncService, never()).startCodeMergeAsync(any(), any(), any());

            // 模拟事务提交后的回调
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // 断言
        verify(pipelineCodeMergeAsyncService).startCodeMergeAsync(eq(800L), eq(List.of(11L)), eq(99L));
        verify(pipelineExecutionService, never()).startCodeMerge(any(), any(), any());
    }

    @Test
    public void testSubmitApplicationReleaseBranch_noPublishedPipeline() {
        // 准备参数
        ChangeDO change = buildChange(11L, "feat/login-1", LocalDateTime.of(2026, 6, 7, 10, 0));
        when(changeMapper.selectListByIds(any())).thenReturn(List.of(change));
        ApplicationEnvDO applicationEnv = buildApplicationEnv(100L, 1L, 10L, 20, 200L);
        when(applicationEnvMapper.selectById(eq(100L))).thenReturn(applicationEnv);
        when(pipelineDefinitionMapper.selectByApplicationEnvId(eq(100L)))
                .thenReturn(buildPipelineDefinition(200L, 100L, null));
        ApplicationReleaseSubmitBranchReqVO reqVO = new ApplicationReleaseSubmitBranchReqVO();
        reqVO.setApplicationEnvId(100L);
        reqVO.setChangeIds(List.of(11L));

        // 调用并断言
        assertServiceException(() -> applicationService.submitApplicationReleaseBranch(reqVO, 99L),
                PIPELINE_PUBLISHED_VERSION_NOT_EXISTS);
    }

    @Test
    public void testSubmitApplicationReleaseBranch_activeRunExists() {
        // 准备参数
        ChangeDO change = buildChange(11L, "feat/login-1", LocalDateTime.of(2026, 6, 7, 10, 0));
        when(changeMapper.selectListByIds(any())).thenReturn(List.of(change));
        ApplicationEnvDO applicationEnv = buildApplicationEnv(100L, 1L, 10L, 20, 200L);
        when(applicationEnvMapper.selectById(eq(100L))).thenReturn(applicationEnv);
        PipelineDefinitionDO definition = buildPipelineDefinition(200L, 100L, 300L);
        when(pipelineDefinitionMapper.selectByApplicationEnvId(eq(100L))).thenReturn(definition);
        PipelineDefinitionVersionDO version = buildPipelineDefinitionVersion(300L, 200L);
        when(pipelineDefinitionVersionMapper.selectById(eq(300L))).thenReturn(version);
        PipelineRunDO activeRun = new PipelineRunDO();
        activeRun.setId(700L);
        activeRun.setApplicationEnvId(100L);
        activeRun.setRunStatus(PipelineRunStatusEnum.RUNNING.getStatus());
        when(pipelineRunMapper.selectListByApplicationEnvIdAndStatuses(eq(100L), any()))
                .thenReturn(List.of(activeRun));
        ApplicationReleaseSubmitBranchReqVO reqVO = new ApplicationReleaseSubmitBranchReqVO();
        reqVO.setApplicationEnvId(100L);
        reqVO.setChangeIds(List.of(11L));

        // 调用并断言
        assertServiceException(() -> applicationService.submitApplicationReleaseBranch(reqVO, 99L),
                PIPELINE_RUN_ACTIVE_EXISTS);
        verify(changeEnvMapper, never()).insert(any(ChangeEnvDO.class));
        verify(changeEnvMapper, never()).updateById(any(ChangeEnvDO.class));
        verify(pipelineRunMapper, never()).insert(any(PipelineRunDO.class));
        verify(pipelineCodeMergeAsyncService, never()).startCodeMergeAsync(any(), any(), any());
    }

    private ApplicationSaveReqVO buildSaveReqVO(Long repositoryProviderId, String repoIdentifier) {
        ApplicationSaveReqVO reqVO = new ApplicationSaveReqVO();
        reqVO.setAppKey("gone-cloud");
        reqVO.setName("Gone Cloud");
        reqVO.setRepositoryProviderId(repositoryProviderId);
        reqVO.setRepoIdentifier(repoIdentifier);
        reqVO.setRepoUrl("https://gitlab.example.com/" + repoIdentifier);
        reqVO.setDefaultBranchName("master");
        reqVO.setOwnerUserId(1L);
        reqVO.setStatus(0);
        return reqVO;
    }

    private RepositoryProviderDO buildRepositoryProvider(Long id, String providerType) {
        RepositoryProviderDO repositoryProvider = new RepositoryProviderDO();
        repositoryProvider.setId(id);
        repositoryProvider.setProviderType(providerType);
        return repositoryProvider;
    }

    private ApplicationDO buildApplication() {
        ApplicationDO application = new ApplicationDO();
        application.setId(1L);
        application.setAppKey("gone-cloud");
        application.setDefaultBranchName("master");
        return application;
    }

    private ApplicationEnvDO buildApplicationEnv(Long id, Long appId, Long envId,
                                                 Integer displayOrder, Long pipelineDefinitionId) {
        ApplicationEnvDO applicationEnv = new ApplicationEnvDO();
        applicationEnv.setId(id);
        applicationEnv.setAppId(appId);
        applicationEnv.setEnvId(envId);
        applicationEnv.setDisplayOrder(displayOrder);
        applicationEnv.setDeployBranchNamePattern("feat/*");
        applicationEnv.setPipelineDefinitionId(pipelineDefinitionId);
        applicationEnv.setStatus(0);
        return applicationEnv;
    }

    private EnvironmentDO buildEnvironment(Long id, String envKey, String envName) {
        EnvironmentDO environment = new EnvironmentDO();
        environment.setId(id);
        environment.setEnvKey(envKey);
        environment.setEnvName(envName);
        environment.setEnvStage("TEST");
        environment.setInfraType("K8S");
        return environment;
    }

    private PipelineDefinitionDO buildPipelineDefinition(Long id, Long applicationEnvId, Long publishedVersionId) {
        PipelineDefinitionDO definition = new PipelineDefinitionDO();
        definition.setId(id);
        definition.setName("测试环境流水线");
        definition.setDefinitionKey("app-env-" + applicationEnvId);
        definition.setApplicationEnvId(applicationEnvId);
        definition.setPublishedVersionId(publishedVersionId);
        return definition;
    }

    private PipelineDefinitionVersionDO buildPipelineDefinitionVersion(Long id, Long definitionId) {
        PipelineDefinitionVersionDO version = new PipelineDefinitionVersionDO();
        version.setId(id);
        version.setDefinitionId(definitionId);
        version.setVersionNo(1);
        version.setVersionName("v1");
        version.setPublishedAt(LocalDateTime.of(2026, 6, 7, 11, 0));
        version.setPublishedBy(1L);
        version.setSpecJson("{\"dslVersion\":\"1.0\"}");
        return version;
    }

    private PipelineSpec buildPipelineSpec() {
        PipelineSpec spec = new PipelineSpec();
        spec.setNodes(List.of(
                buildPipelineNode("checkout", "CHECKOUT", "拉取代码"),
                buildPipelineNode("unit_test", "UNIT_TEST", "单元测试"),
                buildPipelineNode("build_artifact", "BUILD_ARTIFACT", "构建制品")));
        spec.setEdges(List.of(buildPipelineEdge("checkout", "unit_test"),
                buildPipelineEdge("unit_test", "build_artifact")));
        return spec;
    }

    private PipelineSpec.Node buildPipelineNode(String id, String type, String name) {
        PipelineSpec.Node node = new PipelineSpec.Node();
        node.setId(id);
        node.setType(type);
        node.setName(name);
        node.setEnabled(true);
        node.setParams(Map.of("commandTemplateKey", "mvn-test"));
        return node;
    }

    private PipelineSpec.Edge buildPipelineEdge(String source, String target) {
        PipelineSpec.Edge edge = new PipelineSpec.Edge();
        edge.setSource(source);
        edge.setTarget(target);
        return edge;
    }

    private ChangeDO buildChange(Long id, String branchName, LocalDateTime createTime) {
        ChangeDO change = new ChangeDO();
        change.setId(id);
        change.setAppId(1L);
        change.setChangeKey("gone-cloud-" + id);
        change.setTitle("变更 " + id);
        change.setBranchName(branchName);
        change.setSourceBaseBranchName("master");
        change.setOwnerUserId(1L);
        change.setStatus(ChangeStatusEnum.ACTIVE.getStatus());
        change.setLatestCommitSha("sha-" + id);
        change.setLatestCommitMessage("commit " + id);
        change.setLatestCommitAt(createTime.plusMinutes(10));
        change.setCreateTime(createTime);
        return change;
    }

    private ChangeEnvDO buildChangeEnv(Long id, Long changeId, Integer mountStatus) {
        ChangeEnvDO changeEnv = new ChangeEnvDO();
        changeEnv.setId(id);
        changeEnv.setChangeId(changeId);
        changeEnv.setApplicationEnvId(100L);
        changeEnv.setMountStatus(mountStatus);
        changeEnv.setMountedAt(LocalDateTime.of(2026, 6, 7, 12, 0));
        changeEnv.setMountedBy(1L);
        changeEnv.setLastPipelineRunId(1000L + id);
        changeEnv.setLastMergeStatus(MergeStatusEnum.PENDING.getStatus());
        changeEnv.setLastBuildStatus(PipelineStatusEnum.SUCCESS.getStatus());
        changeEnv.setLastTestStatus(PipelineStatusEnum.PENDING.getStatus());
        changeEnv.setLastDeployStatus(PipelineStatusEnum.PENDING.getStatus());
        changeEnv.setApprovalStatus(ApprovalStatusEnum.NONE.getStatus());
        changeEnv.setIncludedInCurrentSnapshot(false);
        return changeEnv;
    }

}
