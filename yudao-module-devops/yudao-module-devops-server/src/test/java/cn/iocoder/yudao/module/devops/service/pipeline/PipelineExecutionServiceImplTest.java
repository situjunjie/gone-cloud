package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.CodeMergeConflictResolutionReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;
import cn.iocoder.yudao.module.devops.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.environment.EnvironmentMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionVersionMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.MergeStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineNodeTypeEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogLevelEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderAuthTypeEnum;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderTypeEnum;
import cn.iocoder.yudao.module.devops.framework.git.GitConflictDescriptor;
import cn.iocoder.yudao.module.devops.framework.git.GitFileResolution;
import cn.iocoder.yudao.module.devops.framework.git.GitMergeResult;
import cn.iocoder.yudao.module.devops.framework.git.GitWorkspacePrepareResult;
import cn.iocoder.yudao.module.devops.framework.git.GitWorkspaceService;
import cn.iocoder.yudao.module.devops.framework.jenkins.JenkinsPipelineClient;
import cn.iocoder.yudao.module.devops.framework.jenkins.JenkinsPipelineStartRequest;
import cn.iocoder.yudao.module.devops.framework.jenkins.JenkinsPipelineStartResult;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.CodeMergeConflictContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.CodeMergeContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.CodeMergeItemContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.CodeMergeResolutionContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelinePlatformNodeAdvanceService;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.cache.annotation.CacheEvict;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PipelineExecutionServiceImpl} 的单元测试。
 */
public class PipelineExecutionServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private PipelineExecutionServiceImpl pipelineExecutionService;

    @Mock
    private PipelineRunMapper pipelineRunMapper;
    @Mock
    private PipelineDefinitionVersionMapper pipelineDefinitionVersionMapper;
    @Mock
    private PipelineRunLogMapper pipelineRunLogMapper;
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
    private RepositoryProviderService repositoryProviderService;
    @Mock
    private GitWorkspaceService gitWorkspaceService;
    @Mock
    private JenkinsPipelineClient jenkinsPipelineClient;
    @Mock
    private PipelinePlatformNodeAdvanceService pipelinePlatformNodeAdvanceService;

    @Test
    public void testWriteOperations_evictCurrentRunCache() throws Exception {
        assertCurrentRunCacheEvict("startCodeMerge", Long.class, List.class, Long.class);
        assertCurrentRunCacheEvict("saveCodeMergeConflictResolution", Long.class,
                CodeMergeConflictResolutionReqVO.class, Long.class);
        assertCurrentRunCacheEvict("continueCodeMerge", Long.class, Long.class);
        assertCurrentRunCacheEvict("retryCurrentCodeMergeChange", Long.class, Long.class);
        assertCurrentRunCacheEvict("cancelRun", Long.class, Long.class);
    }

    @Test
    public void testStartCodeMerge_success() {
        // 准备参数
        PipelineRunDO run = buildRun();
        mockBaseRunContext(run);
        when(changeMapper.selectListByIds(eq(List.of(11L, 12L))))
                .thenReturn(List.of(buildChange(11L, "feat/a"), buildChange(12L, "feat/b")));
        doAnswer(invocation -> {
            PipelineRunLogDO log = invocation.getArgument(0);
            log.setId(900L);
            return 1;
        }).when(pipelineRunLogMapper).insert(any(PipelineRunLogDO.class));
        GitWorkspacePrepareResult prepareResult = new GitWorkspacePrepareResult();
        prepareResult.setWorkspaceKey("run-800");
        prepareResult.setBaseCommitSha("base-sha");
        when(gitWorkspaceService.prepareWorkspace(eq(800L), eq("https://gitlab/group/repo.git"), eq("token"),
                eq("master"), eq("release/test/20260607120000"))).thenReturn(prepareResult);
        when(gitWorkspaceService.merge(eq("run-800"), eq("sha-11"), any()))
                .thenReturn(buildMergeSuccess("merge-11"));
        when(gitWorkspaceService.merge(eq("run-800"), eq("sha-12"), any()))
                .thenReturn(buildMergeSuccess("merge-12"));
        mockJenkinsSkipped();

        // 调用
        pipelineExecutionService.startCodeMerge(800L, List.of(11L, 12L), 99L);

        // 断言
        verify(gitWorkspaceService).pushDeployBranch(eq("run-800"), eq("release/test/20260607120000"));
        verify(gitWorkspaceService).cleanup(eq("run-800"));
        verify(pipelinePlatformNodeAdvanceService).advance(eq(run), any(PipelineDefinitionVersionDO.class));
    }

    @Test
    public void testStartCodeMerge_successTriggerJenkins() {
        // 准备参数
        PipelineRunDO run = buildRun();
        mockBaseRunContext(run);
        when(changeMapper.selectListByIds(eq(List.of(11L))))
                .thenReturn(List.of(buildChange(11L, "feat/a")));
        doAnswer(invocation -> {
            PipelineRunLogDO log = invocation.getArgument(0);
            log.setId(900L);
            return 1;
        }).when(pipelineRunLogMapper).insert(any(PipelineRunLogDO.class));
        GitWorkspacePrepareResult prepareResult = new GitWorkspacePrepareResult();
        prepareResult.setWorkspaceKey("run-800");
        prepareResult.setBaseCommitSha("base-sha");
        when(gitWorkspaceService.prepareWorkspace(any(), any(), any(), any(), any())).thenReturn(prepareResult);
        when(gitWorkspaceService.merge(eq("run-800"), eq("sha-11"), any()))
                .thenReturn(buildMergeSuccess("merge-11"));
        when(jenkinsPipelineClient.startPipeline(any())).thenReturn(new JenkinsPipelineStartResult(false, "queue-1"));
        mockPipelineVersion(mixedJenkinsAndContainerSpec(), "pipeline {}");

        // 调用
        pipelineExecutionService.startCodeMerge(800L, List.of(11L), 99L);

        // 断言
        ArgumentCaptor<JenkinsPipelineStartRequest> requestCaptor = ArgumentCaptor.forClass(JenkinsPipelineStartRequest.class);
        verify(jenkinsPipelineClient).startPipeline(requestCaptor.capture());
        assertEquals(800L, requestCaptor.getValue().getPipelineRunId());
        assertEquals(300L, requestCaptor.getValue().getPipelineVersionId());
        assertEquals("release/test/20260607120000", requestCaptor.getValue().getBranchName());
        assertEquals("merge-11", requestCaptor.getValue().getCommitSha());
        ArgumentCaptor<PipelineRunDO> runCaptor = ArgumentCaptor.forClass(PipelineRunDO.class);
        verify(pipelineRunMapper).updateById(runCaptor.capture());
        PipelineRunDO updatedRun = runCaptor.getAllValues().get(runCaptor.getAllValues().size() - 1);
        assertEquals(PipelineRunStatusEnum.RUNNING.getStatus(), updatedRun.getRunStatus());
        assertEquals("queue-1", updatedRun.getJenkinsQueueId());
    }

    @Test
    public void testStartCodeMerge_containerOnlyPipelineStartPlatformDeployDirectly() {
        // 准备参数
        PipelineRunDO run = buildRun();
        mockBaseRunContext(run);
        when(changeMapper.selectListByIds(eq(List.of(11L))))
                .thenReturn(List.of(buildChange(11L, "feat/a")));
        doAnswer(invocation -> {
            PipelineRunLogDO log = invocation.getArgument(0);
            log.setId(900L);
            return 1;
        }).when(pipelineRunLogMapper).insert(any(PipelineRunLogDO.class));
        GitWorkspacePrepareResult prepareResult = new GitWorkspacePrepareResult();
        prepareResult.setWorkspaceKey("run-800");
        prepareResult.setBaseCommitSha("base-sha");
        when(gitWorkspaceService.prepareWorkspace(any(), any(), any(), any(), any())).thenReturn(prepareResult);
        when(gitWorkspaceService.merge(eq("run-800"), eq("sha-11"), any()))
                .thenReturn(buildMergeSuccess("merge-11"));
        mockPipelineVersion(containerOnlySpec(), "pipeline { stages {} }");

        // 调用
        pipelineExecutionService.startCodeMerge(800L, List.of(11L), 99L);

        // 断言
        verify(jenkinsPipelineClient, never()).startPipeline(any());
        verify(pipelinePlatformNodeAdvanceService).advance(eq(run), any(PipelineDefinitionVersionDO.class));
    }

    @Test
    public void testStartCodeMerge_legacyRunBranchNameFallback() {
        // 准备参数
        PipelineRunDO run = buildRun();
        run.setBranchName("feat/a");
        mockBaseRunContext(run);
        when(changeMapper.selectListByIds(eq(List.of(11L))))
                .thenReturn(List.of(buildChange(11L, "feat/a")));
        doAnswer(invocation -> {
            PipelineRunLogDO log = invocation.getArgument(0);
            log.setId(900L);
            return 1;
        }).when(pipelineRunLogMapper).insert(any(PipelineRunLogDO.class));
        GitWorkspacePrepareResult prepareResult = new GitWorkspacePrepareResult();
        prepareResult.setWorkspaceKey("run-800");
        prepareResult.setBaseCommitSha("base-sha");
        when(gitWorkspaceService.prepareWorkspace(eq(800L), eq("https://gitlab/group/repo.git"), eq("token"),
                eq("master"), eq("deploy/gone/test/800"))).thenReturn(prepareResult);
        when(gitWorkspaceService.merge(eq("run-800"), eq("sha-11"), any()))
                .thenReturn(buildMergeSuccess("merge-11"));
        mockJenkinsSkipped();

        // 调用
        pipelineExecutionService.startCodeMerge(800L, List.of(11L), 99L);

        // 断言
        verify(gitWorkspaceService).pushDeployBranch(eq("run-800"), eq("deploy/gone/test/800"));
    }

    @Test
    public void testStartCodeMerge_emptyChangesReleaseBaseBranch() {
        // 准备参数
        PipelineRunDO run = buildRun();
        mockBaseRunContext(run);
        doAnswer(invocation -> {
            PipelineRunLogDO log = invocation.getArgument(0);
            log.setId(900L);
            return 1;
        }).when(pipelineRunLogMapper).insert(any(PipelineRunLogDO.class));
        GitWorkspacePrepareResult prepareResult = new GitWorkspacePrepareResult();
        prepareResult.setWorkspaceKey("run-800");
        prepareResult.setBaseCommitSha("base-sha");
        when(gitWorkspaceService.prepareWorkspace(eq(800L), eq("https://gitlab/group/repo.git"), eq("token"),
                eq("master"), eq("release/test/20260607120000"))).thenReturn(prepareResult);
        when(jenkinsPipelineClient.startPipeline(any())).thenReturn(new JenkinsPipelineStartResult(false, "queue-1"));
        mockPipelineVersion();

        // 调用
        pipelineExecutionService.startCodeMerge(800L, List.of(), 99L);

        // 断言
        verify(changeMapper, never()).selectListByIds(any());
        verify(gitWorkspaceService, never()).merge(any(), any(), any());
        verify(gitWorkspaceService).pushDeployBranch(eq("run-800"), eq("release/test/20260607120000"));
        ArgumentCaptor<JenkinsPipelineStartRequest> requestCaptor = ArgumentCaptor.forClass(JenkinsPipelineStartRequest.class);
        verify(jenkinsPipelineClient).startPipeline(requestCaptor.capture());
        assertEquals("release/test/20260607120000", requestCaptor.getValue().getBranchName());
        assertEquals("base-sha", requestCaptor.getValue().getCommitSha());
    }

    @Test
    public void testStartCodeMerge_conflictWaitingInput() {
        // 准备参数
        PipelineRunDO run = buildRun();
        mockBaseRunContext(run);
        when(changeMapper.selectListByIds(eq(List.of(11L))))
                .thenReturn(List.of(buildChange(11L, "feat/a")));
        doAnswer(invocation -> {
            PipelineRunLogDO log = invocation.getArgument(0);
            log.setId(900L);
            return 1;
        }).when(pipelineRunLogMapper).insert(any(PipelineRunLogDO.class));
        GitWorkspacePrepareResult prepareResult = new GitWorkspacePrepareResult();
        prepareResult.setWorkspaceKey("run-800");
        prepareResult.setBaseCommitSha("base-sha");
        when(gitWorkspaceService.prepareWorkspace(any(), any(), any(), any(), any())).thenReturn(prepareResult);
        when(gitWorkspaceService.merge(eq("run-800"), eq("sha-11"), any()))
                .thenReturn(buildMergeConflict());

        // 调用
        pipelineExecutionService.startCodeMerge(800L, List.of(11L), 99L);

        // 断言
        verify(gitWorkspaceService, never()).pushDeployBranch(any(), any());
        ArgumentCaptor<PipelineRunLogDO> logCaptor = ArgumentCaptor.forClass(PipelineRunLogDO.class);
        verify(pipelineRunLogMapper, org.mockito.Mockito.atLeastOnce()).updateById(logCaptor.capture());
        PipelineRunLogDO lastLog = logCaptor.getAllValues().get(logCaptor.getAllValues().size() - 1);
        assertEquals(PipelineRunLogStatusEnum.WAITING_INPUT.getStatus(), lastLog.getStatus());

        ArgumentCaptor<ChangeEnvDO> changeEnvCaptor = ArgumentCaptor.forClass(ChangeEnvDO.class);
        verify(changeEnvMapper, org.mockito.Mockito.times(2)).updateById(changeEnvCaptor.capture());
        assertEquals(MergeStatusEnum.CONFLICT.getStatus(),
                changeEnvCaptor.getAllValues().get(changeEnvCaptor.getAllValues().size() - 1).getLastMergeStatus());
    }

    @Test
    public void testSaveCodeMergeConflictResolution_success() {
        // 准备参数
        PipelineRunLogDO log = buildWaitingCodeMergeLog(false);
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeType(eq(800L), eq(PipelineNodeTypeEnum.CODE_MERGE.getType())))
                .thenReturn(log);
        CodeMergeConflictResolutionReqVO reqVO = new CodeMergeConflictResolutionReqVO();
        reqVO.setFilePath("src/App.java");
        reqVO.setResolutionType("MANUAL");
        reqVO.setResolvedContent("resolved");
        reqVO.setComment("ok");

        // 调用
        pipelineExecutionService.saveCodeMergeConflictResolution(800L, reqVO, 99L);

        // 断言
        ArgumentCaptor<PipelineRunLogDO> logCaptor = ArgumentCaptor.forClass(PipelineRunLogDO.class);
        verify(pipelineRunLogMapper).updateById(logCaptor.capture());
        CodeMergeContext context = JsonUtils.parseObject(logCaptor.getValue().getContextJson(), CodeMergeContext.class);
        assertEquals(CodeMergeConflictContext.STATUS_RESOLVED, context.getConflicts().get(0).getStatus());
        assertEquals("resolved", context.getResolutions().get(0).getResolvedContent());
        verify(pipelineRunLogMapper).insert(any(PipelineRunLogDO.class));
    }

    @Test
    public void testContinueCodeMerge_success() {
        // 准备参数
        PipelineRunDO run = buildRun();
        PipelineRunLogDO log = buildWaitingCodeMergeLog(true);
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(run);
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeType(eq(800L), eq(PipelineNodeTypeEnum.CODE_MERGE.getType())))
                .thenReturn(log);
        when(gitWorkspaceService.continueMerge(eq("run-800"), eq(List.of(new GitFileResolution("src/App.java", "resolved"))),
                any())).thenReturn("merge-11");
        mockJenkinsSkipped();

        // 调用
        pipelineExecutionService.continueCodeMerge(800L, 99L);

        // 断言
        verify(gitWorkspaceService).pushDeployBranch(eq("run-800"), eq("release/test/20260607120000"));
        verify(gitWorkspaceService).cleanup(eq("run-800"));
        verify(pipelinePlatformNodeAdvanceService).advance(eq(run), any(PipelineDefinitionVersionDO.class));
    }

    @Test
    public void testRetryCurrentCodeMergeChange_success() {
        // 准备参数
        PipelineRunDO run = buildRun();
        PipelineRunLogDO log = buildWaitingCodeMergeLog(false);
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(run);
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeType(eq(800L), eq(PipelineNodeTypeEnum.CODE_MERGE.getType())))
                .thenReturn(log);
        when(gitWorkspaceService.resolveRemoteBranchCommit(eq("run-800"), eq("feat/a"))).thenReturn("sha-new");
        when(gitWorkspaceService.merge(eq("run-800"), eq("sha-new"), any()))
                .thenReturn(buildMergeSuccess("merge-new"));
        mockJenkinsSkipped();

        // 调用
        pipelineExecutionService.retryCurrentCodeMergeChange(800L, 99L);

        // 断言
        verify(gitWorkspaceService).abortMerge(eq("run-800"));
        verify(gitWorkspaceService).pushDeployBranch(eq("run-800"), eq("release/test/20260607120000"));
        verify(pipelinePlatformNodeAdvanceService).advance(eq(run), any(PipelineDefinitionVersionDO.class));
    }

    @Test
    public void testCancelRun_success() {
        // 准备参数
        PipelineRunDO run = buildRun();
        PipelineRunLogDO log = buildWaitingCodeMergeLog(false);
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(run);
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeType(eq(800L), eq(PipelineNodeTypeEnum.CODE_MERGE.getType())))
                .thenReturn(log);

        // 调用
        pipelineExecutionService.cancelRun(800L, 99L);

        // 断言
        verify(gitWorkspaceService).abortMerge(eq("run-800"));
        verify(gitWorkspaceService).cleanup(eq("run-800"));
        ArgumentCaptor<PipelineRunLogDO> logCaptor = ArgumentCaptor.forClass(PipelineRunLogDO.class);
        verify(pipelineRunLogMapper, times(1)).updateById(logCaptor.capture());
        assertEquals(PipelineRunLogStatusEnum.CANCELED.getStatus(), logCaptor.getValue().getStatus());
        ArgumentCaptor<PipelineRunDO> runCaptor = ArgumentCaptor.forClass(PipelineRunDO.class);
        verify(pipelineRunMapper).updateById(runCaptor.capture());
        assertEquals(PipelineRunStatusEnum.CANCELED.getStatus(), runCaptor.getValue().getRunStatus());
    }

    private void assertCurrentRunCacheEvict(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = PipelineExecutionServiceImpl.class.getMethod(methodName, parameterTypes);
        CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);
        assertEquals(RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN, cacheEvict.value()[0]);
        assertEquals("#root.target.getApplicationEnvIdByPipelineRunId(#pipelineRunId)", cacheEvict.key());
    }

    private void mockBaseRunContext(PipelineRunDO run) {
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(run);
        when(pipelineRunMapper.selectListByApplicationEnvIdAndStatuses(eq(100L), any()))
                .thenReturn(List.of(run));
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeType(eq(800L), any())).thenReturn(null);
        ApplicationEnvDO applicationEnv = new ApplicationEnvDO();
        applicationEnv.setId(100L);
        applicationEnv.setAppId(1L);
        applicationEnv.setEnvId(10L);
        when(applicationEnvMapper.selectById(eq(100L))).thenReturn(applicationEnv);
        ApplicationDO application = new ApplicationDO();
        application.setId(1L);
        application.setAppKey("gone");
        application.setRepositoryProviderId(20L);
        application.setRepoUrl("https://gitlab/group/repo.git");
        application.setDefaultBranchName("master");
        lenient().when(applicationMapper.selectById(eq(1L))).thenReturn(application);
        EnvironmentDO environment = new EnvironmentDO();
        environment.setId(10L);
        environment.setEnvKey("test");
        when(environmentMapper.selectById(eq(10L))).thenReturn(environment);
        RepositoryProviderDO provider = new RepositoryProviderDO();
        provider.setId(20L);
        provider.setProviderType(RepositoryProviderTypeEnum.GITLAB.getProviderType());
        provider.setAuthType(RepositoryProviderAuthTypeEnum.ACCESS_TOKEN.getAuthType());
        provider.setAccessToken("token");
        when(repositoryProviderService.getRepositoryProvider(eq(20L))).thenReturn(provider);
        lenient().when(changeEnvMapper.selectByChangeIdAndApplicationEnvId(any(), eq(100L))).thenAnswer(invocation -> {
            ChangeEnvDO changeEnv = new ChangeEnvDO();
            changeEnv.setChangeId(invocation.getArgument(0));
            changeEnv.setApplicationEnvId(100L);
            return changeEnv;
        });
    }

    private PipelineRunDO buildRun() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setDefinitionVersionId(300L);
        run.setAppId(1L);
        run.setApplicationEnvId(100L);
        run.setBranchName("release/test/20260607120000");
        run.setRunStatus(PipelineRunStatusEnum.RUNNING.getStatus());
        return run;
    }

    private void mockJenkinsSkipped() {
        mockPipelineVersion(jenkinsOnlySpec(), "pipeline {}");
        mockApplicationForJenkins();
        when(jenkinsPipelineClient.startPipeline(any())).thenReturn(new JenkinsPipelineStartResult(true, null));
    }

    private void mockPipelineVersion() {
        mockPipelineVersion(jenkinsOnlySpec(), "pipeline {}");
    }

    private void mockPipelineVersion(PipelineSpec spec, String jenkinsfileText) {
        PipelineDefinitionVersionDO version = new PipelineDefinitionVersionDO();
        version.setId(300L);
        version.setSpecJson(JsonUtils.toJsonString(spec));
        version.setJenkinsfileText(jenkinsfileText);
        when(pipelineDefinitionVersionMapper.selectById(eq(300L))).thenReturn(version);
    }

    private void mockApplicationForJenkins() {
        ApplicationDO application = new ApplicationDO();
        application.setId(1L);
        application.setAppKey("gone");
        application.setRepositoryProviderId(20L);
        application.setRepoUrl("https://gitlab/group/repo.git");
        application.setDefaultBranchName("master");
        when(applicationMapper.selectById(eq(1L))).thenReturn(application);
    }

    private ChangeDO buildChange(Long id, String branchName) {
        ChangeDO change = new ChangeDO();
        change.setId(id);
        change.setChangeKey("change-" + id);
        change.setBranchName(branchName);
        change.setLatestCommitSha("sha-" + id);
        return change;
    }

    private GitMergeResult buildMergeSuccess(String mergeCommitSha) {
        GitMergeResult result = new GitMergeResult();
        result.setStatus(GitMergeResult.STATUS_SUCCESS);
        result.setMergeCommitSha(mergeCommitSha);
        result.setConflicts(List.of());
        return result;
    }

    private GitMergeResult buildMergeConflict() {
        GitConflictDescriptor conflict = new GitConflictDescriptor();
        conflict.setFilePath("src/App.java");
        conflict.setConflictType("TEXT");
        conflict.setText(true);
        GitMergeResult result = new GitMergeResult();
        result.setStatus(GitMergeResult.STATUS_CONFLICTING);
        result.setConflicts(List.of(conflict));
        result.setOutput("conflict");
        return result;
    }

    private PipelineSpec jenkinsOnlySpec() {
        PipelineSpec spec = new PipelineSpec();
        spec.setNodes(List.of(node("checkout", PipelineNodeRegistryServiceImpl.TYPE_CHECKOUT)));
        return spec;
    }

    private PipelineSpec containerOnlySpec() {
        PipelineSpec spec = new PipelineSpec();
        spec.setNodes(List.of(node("deploy", PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY)));
        return spec;
    }

    private PipelineSpec mixedJenkinsAndContainerSpec() {
        PipelineSpec spec = new PipelineSpec();
        spec.setNodes(List.of(node("docker", PipelineNodeRegistryServiceImpl.TYPE_DOCKER_BUILD_PUSH),
                node("deploy", PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY)));
        return spec;
    }

    private PipelineSpec.Node node(String id, String type) {
        PipelineSpec.Node node = new PipelineSpec.Node();
        node.setId(id);
        node.setType(type);
        node.setName(id);
        return node;
    }

    private PipelineRunLogDO buildWaitingCodeMergeLog(boolean resolved) {
        PipelineRunLogDO log = new PipelineRunLogDO();
        log.setId(900L);
        log.setPipelineRunId(800L);
        log.setNodeId("builtin.code_merge");
        log.setNodeType(PipelineNodeTypeEnum.CODE_MERGE.getType());
        log.setNodeName("代码合并");
        log.setLogLevel(PipelineRunLogLevelEnum.NODE.getLevel());
        log.setStatus(PipelineRunLogStatusEnum.WAITING_INPUT.getStatus());
        log.setStartedAt(LocalDateTime.now());
        CodeMergeContext context = new CodeMergeContext();
        context.setDeployBranch("release/test/20260607120000");
        context.setWorkspaceKey("run-800");
        context.setCurrentChangeId(11L);
        CodeMergeItemContext item = new CodeMergeItemContext();
        item.setChangeId(11L);
        item.setChangeKey("change-11");
        item.setBranchName("feat/a");
        item.setCommitSha("sha-11");
        item.setStatus(CodeMergeItemContext.STATUS_CONFLICTING);
        context.setItems(List.of(item));
        CodeMergeConflictContext conflict = new CodeMergeConflictContext();
        conflict.setFilePath("src/App.java");
        conflict.setConflictType("TEXT");
        conflict.setStatus(resolved ? CodeMergeConflictContext.STATUS_RESOLVED
                : CodeMergeConflictContext.STATUS_UNRESOLVED);
        conflict.setText(true);
        context.setConflicts(List.of(conflict));
        if (resolved) {
            CodeMergeResolutionContext resolution = new CodeMergeResolutionContext();
            resolution.setFilePath("src/App.java");
            resolution.setResolutionType("MANUAL");
            resolution.setResolvedContent("resolved");
            resolution.setResolvedBy(99L);
            resolution.setResolvedAt(LocalDateTime.now());
            context.setResolutions(List.of(resolution));
        }
        log.setContextJson(JsonUtils.toJsonString(context));
        return log;
    }

}
