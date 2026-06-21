package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.job.PipelineRunJobDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionVersionMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.job.PipelineRunJobMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineRunJobStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCacheConfigResolver;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntimeManager;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineWorkspace;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineWorkspaceService;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineSpecValidationService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepHandler;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepHandlerRegistry;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.StepResult;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.StepRuntimeRequirement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PipelineExecutionEngine} 单元测试。
 */
class PipelineExecutionEngineTest {

    @Mock
    private PipelineDefinitionVersionMapper pipelineDefinitionVersionMapper;
    @Mock
    private PipelineRunMapper pipelineRunMapper;
    @Mock
    private PipelineRunJobMapper pipelineRunJobMapper;
    @Mock
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Mock
    private PipelineSpecValidationService pipelineSpecValidationService;
    @Mock
    private PipelineStepHandlerRegistry stepHandlerRegistry;
    @Mock
    private PipelineJobRuntimeManager pipelineJobRuntimeManager;
    @Mock
    private PipelineWorkspaceService pipelineWorkspaceService;
    @Mock
    private PipelineCacheConfigResolver pipelineCacheConfigResolver;
    @Mock
    private PipelineSourceWorkspacePreparer pipelineSourceWorkspacePreparer;

    @InjectMocks
    private PipelineExecutionEngine engine;

    private final Map<String, PipelineRunJobDO> jobStore = Collections.synchronizedMap(new LinkedHashMap<>());
    private ExecutorService jobExecutor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jobExecutor = Executors.newFixedThreadPool(4);
        ReflectionTestUtils.setField(engine, "pipelineJobExecutionExecutor", jobExecutor);
        mockJobMapper();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (jobExecutor != null) {
            jobExecutor.shutdownNow();
        }
    }

    @Test
    void testExecute_jobsByNeeds_success() {
        PipelineRunDO run = run(1L);
        PipelineDefinitionVersionDO version = version(100L);
        PipelineSpec spec = specWithNeeds(false);
        List<String> executedSteps = new ArrayList<>();
        PipelineStepHandler handler = handler(StepResult.continueWith("ok"), executedSteps);

        when(pipelineDefinitionVersionMapper.selectById(100L)).thenReturn(version);
        when(pipelineSpecValidationService.parseSpec(any(), any())).thenReturn(spec);
        when(stepHandlerRegistry.resolve(PipelineNodeRegistryServiceImpl.TYPE_COMMAND)).thenReturn(handler);
        when(pipelineCacheConfigResolver.resolveVersionConfig(any())).thenReturn(null);
        when(pipelineWorkspaceService.createWorkspace(any(), any(), any())).thenReturn(workspace());
        when(pipelineJobRuntimeManager.createRuntime(any(), any(), any())).thenReturn(PipelineJobRuntime.builder()
                .runtimeType("DOCKER")
                .runtimeId("container-1")
                .runtimeName("pipeline-1")
                .executorGroup("local-docker/default")
                .executorImage("eclipse-temurin:17")
                .workspace(Path.of("/tmp/workspace"))
                .build());

        engine.execute(run, 999L);

        assertEquals(List.of("test_step", "build_step"), executedSteps);
        assertEquals(PipelineRunJobStatusEnum.SUCCESS.getStatus(), jobStore.get("test_job").getStatus());
        assertEquals(PipelineRunJobStatusEnum.SUCCESS.getStatus(), jobStore.get("build_job").getStatus());
        verify(pipelineSourceWorkspacePreparer, org.mockito.Mockito.times(1))
                .prepare(any(), org.mockito.Mockito.eq(spec), org.mockito.Mockito.eq(Path.of("/tmp/workspace")), any());
        ArgumentCaptor<PipelineRunDO> runCaptor = ArgumentCaptor.forClass(PipelineRunDO.class);
        verify(pipelineRunMapper, org.mockito.Mockito.atLeastOnce()).updateById(runCaptor.capture());
        assertEquals(PipelineRunStatusEnum.SUCCESS.getStatus(), runCaptor.getAllValues().get(runCaptor.getAllValues().size() - 1).getRunStatus());
    }

    @Test
    void testExecute_dependencyFailed_downstreamSkipped() {
        PipelineRunDO run = run(2L);
        PipelineDefinitionVersionDO version = version(200L);
        PipelineSpec spec = specWithNeeds(false);
        PipelineStepHandler handler = handler(StepResult.fail("fail", "boom"), new ArrayList<>());

        when(pipelineDefinitionVersionMapper.selectById(200L)).thenReturn(version);
        when(pipelineSpecValidationService.parseSpec(any(), any())).thenReturn(spec);
        when(stepHandlerRegistry.resolve(PipelineNodeRegistryServiceImpl.TYPE_COMMAND)).thenReturn(handler);
        when(pipelineCacheConfigResolver.resolveVersionConfig(any())).thenReturn(null);
        when(pipelineWorkspaceService.createWorkspace(any(), any(), any())).thenReturn(workspace());
        when(pipelineJobRuntimeManager.createRuntime(any(), any(), any())).thenReturn(PipelineJobRuntime.builder()
                .runtimeType("DOCKER")
                .runtimeId("container-2")
                .runtimeName("pipeline-2")
                .executorGroup("local-docker/default")
                .executorImage("eclipse-temurin:17")
                .workspace(Path.of("/tmp/workspace"))
                .build());

        engine.execute(run, 999L);

        assertEquals(PipelineRunJobStatusEnum.FAILED.getStatus(), jobStore.get("test_job").getStatus());
        assertEquals(PipelineRunJobStatusEnum.SKIPPED.getStatus(), jobStore.get("build_job").getStatus());
    }

    @Test
    void testExecute_inputContextInjectedToSharedState() {
        PipelineRunDO run = run(4L);
        run.setInputContextJson(JsonUtils.toJsonString(Map.of("fileUrl",
                "https://example.com/images/demo.oci.tar.zst")));
        PipelineDefinitionVersionDO version = version(400L);
        PipelineSpec spec = specWithCommandRun("echo ${FILE_URL}");
        List<String> resolvedScripts = new ArrayList<>();
        PipelineStepHandler handler = org.mockito.Mockito.mock(PipelineStepHandler.class);
        when(handler.runtimeRequirement()).thenReturn(StepRuntimeRequirement.JOB_RUNTIME);
        when(handler.handle(any())).thenAnswer(invocation -> {
            PipelineStepContext ctx = invocation.getArgument(0);
            resolvedScripts.add(String.valueOf(ctx.getResolvedWith().get("run")));
            return StepResult.continueWith("ok");
        });

        when(pipelineDefinitionVersionMapper.selectById(400L)).thenReturn(version);
        when(pipelineSpecValidationService.parseSpec(any(), any())).thenReturn(spec);
        when(stepHandlerRegistry.resolve(PipelineNodeRegistryServiceImpl.TYPE_COMMAND)).thenReturn(handler);
        when(pipelineCacheConfigResolver.resolveVersionConfig(any())).thenReturn(null);
        when(pipelineWorkspaceService.createWorkspace(any(), any(), any())).thenReturn(workspace());
        when(pipelineJobRuntimeManager.createRuntime(any(), any(), any())).thenReturn(PipelineJobRuntime.builder()
                .runtimeType("DOCKER")
                .runtimeId("container-4")
                .runtimeName("pipeline-4")
                .executorGroup("local-docker/default")
                .executorImage("eclipse-temurin:17")
                .workspace(Path.of("/tmp/workspace"))
                .build());

        engine.execute(run, 999L);

        assertEquals(List.of("echo https://example.com/images/demo.oci.tar.zst"), resolvedScripts);
    }

    @Test
    void testExecute_readyJobsWithSameDependency_runInParallel() {
        PipelineRunDO run = run(3L);
        PipelineDefinitionVersionDO version = version(300L);
        PipelineSpec spec = specWithParallelJobs();
        CountDownLatch parallelJobsStarted = new CountDownLatch(2);
        AtomicBoolean parallelObserved = new AtomicBoolean(true);
        List<String> executedSteps = Collections.synchronizedList(new ArrayList<>());
        PipelineStepHandler handler = handlerByStep(executedSteps, parallelJobsStarted, parallelObserved);

        when(pipelineDefinitionVersionMapper.selectById(300L)).thenReturn(version);
        when(pipelineSpecValidationService.parseSpec(any(), any())).thenReturn(spec);
        when(stepHandlerRegistry.resolve(PipelineNodeRegistryServiceImpl.TYPE_COMMAND)).thenReturn(handler);
        when(pipelineCacheConfigResolver.resolveVersionConfig(any())).thenReturn(null);
        when(pipelineWorkspaceService.createWorkspace(any(), any(), any())).thenReturn(workspace());
        when(pipelineJobRuntimeManager.createRuntime(any(), any(), any())).thenReturn(PipelineJobRuntime.builder()
                .runtimeType("DOCKER")
                .runtimeId("container-3")
                .runtimeName("pipeline-3")
                .executorGroup("local-docker/default")
                .executorImage("eclipse-temurin:17")
                .workspace(Path.of("/tmp/workspace"))
                .build());

        engine.execute(run, 999L);

        assertTrue(parallelObserved.get(), "同一上游完成后的两个 ready jobs 应该并行执行");
        assertTrue(executedSteps.indexOf("code_merge_step") < executedSteps.indexOf("test_command_step"));
        assertTrue(executedSteps.indexOf("code_merge_step") < executedSteps.indexOf("test_command_step2"));
        assertTrue(executedSteps.indexOf("test_command_step") < executedSteps.indexOf("build_step"));
        assertTrue(executedSteps.indexOf("test_command_step2") < executedSteps.indexOf("build_step"));
        assertEquals(PipelineRunJobStatusEnum.SUCCESS.getStatus(), jobStore.get("unit_test_job").getStatus());
        assertEquals(PipelineRunJobStatusEnum.SUCCESS.getStatus(), jobStore.get("unit_test_job2").getStatus());
        assertEquals(PipelineRunJobStatusEnum.SUCCESS.getStatus(), jobStore.get("build_job").getStatus());
    }

    private PipelineStepHandler handler(StepResult result, List<String> executedSteps) {
        PipelineStepHandler handler = org.mockito.Mockito.mock(PipelineStepHandler.class);
        when(handler.runtimeRequirement()).thenReturn(StepRuntimeRequirement.JOB_RUNTIME);
        when(handler.handle(any())).thenAnswer(invocation -> {
            PipelineStepContext ctx = invocation.getArgument(0);
            executedSteps.add(ctx.getStep().getStepId());
            return result;
        });
        return handler;
    }

    private PipelineStepHandler handlerByStep(List<String> executedSteps, CountDownLatch parallelJobsStarted,
                                              AtomicBoolean parallelObserved) {
        PipelineStepHandler handler = org.mockito.Mockito.mock(PipelineStepHandler.class);
        when(handler.runtimeRequirement()).thenReturn(StepRuntimeRequirement.JOB_RUNTIME);
        when(handler.handle(any())).thenAnswer(invocation -> {
            PipelineStepContext ctx = invocation.getArgument(0);
            String stepId = ctx.getStep().getStepId();
            executedSteps.add(stepId);
            if ("test_command_step".equals(stepId) || "test_command_step2".equals(stepId)) {
                parallelJobsStarted.countDown();
                if (!parallelJobsStarted.await(2, TimeUnit.SECONDS)) {
                    parallelObserved.set(false);
                }
            }
            return StepResult.continueWith("ok");
        });
        return handler;
    }

    private PipelineRunDO run(Long id) {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(id);
        run.setDefinitionVersionId(id * 100);
        run.setRunStatus(PipelineRunStatusEnum.QUEUED.getStatus());
        return run;
    }

    private PipelineDefinitionVersionDO version(Long id) {
        PipelineDefinitionVersionDO version = new PipelineDefinitionVersionDO();
        version.setId(id);
        version.setSpecJson(JsonUtils.toJsonString(new PipelineSpec()));
        return version;
    }

    private PipelineSpec specWithNeeds(boolean reverse) {
        PipelineSpec spec = new PipelineSpec();
        PipelineSpec.Stage stage = new PipelineSpec.Stage();
        stage.setName("构建");
        stage.setJobs(new LinkedHashMap<>());
        PipelineSpec.Job testJob = job("测试", "test_step");
        PipelineSpec.Job buildJob = job("构建", "build_step");
        buildJob.setNeeds("test_job");
        if (reverse) {
            stage.getJobs().put("build_job", buildJob);
            stage.getJobs().put("test_job", testJob);
        } else {
            stage.getJobs().put("test_job", testJob);
            stage.getJobs().put("build_job", buildJob);
        }
        spec.setStages(new LinkedHashMap<>());
        spec.getStages().put("build_stage", stage);
        return spec;
    }

    private PipelineSpec specWithCommandRun(String runScript) {
        PipelineSpec spec = new PipelineSpec();
        PipelineSpec.Stage stage = new PipelineSpec.Stage();
        stage.setName("构建");
        stage.setJobs(new LinkedHashMap<>());
        stage.getJobs().put("test_job", buildCommandJob(runScript));
        spec.setStages(new LinkedHashMap<>());
        spec.getStages().put("build_stage", stage);
        return spec;
    }

    private PipelineSpec.Job buildCommandJob(String runScript) {
        PipelineSpec.Job job = job("测试", "test_step");
        job.getSteps().get("test_step").setWith(Map.of("run", runScript));
        return job;
    }

    private PipelineSpec specWithParallelJobs() {
        PipelineSpec spec = new PipelineSpec();
        spec.setStages(new LinkedHashMap<>());

        PipelineSpec.Stage mergeStage = new PipelineSpec.Stage();
        mergeStage.setName("代码合并");
        mergeStage.setJobs(new LinkedHashMap<>());
        mergeStage.getJobs().put("code_merge_job", job("合并变更分支", "code_merge_step"));
        spec.getStages().put("merge_stage", mergeStage);

        PipelineSpec.Stage testStage = new PipelineSpec.Stage();
        testStage.setName("测试");
        testStage.setJobs(new LinkedHashMap<>());
        PipelineSpec.Job unitTestJob = job("单元测试", "test_command_step");
        unitTestJob.setNeeds(List.of("code_merge_job"));
        testStage.getJobs().put("unit_test_job", unitTestJob);
        PipelineSpec.Job unitTestJob2 = job("单元测试并行", "test_command_step2");
        unitTestJob2.setNeeds(List.of("code_merge_job"));
        testStage.getJobs().put("unit_test_job2", unitTestJob2);
        spec.getStages().put("test_stage", testStage);

        PipelineSpec.Stage buildStage = new PipelineSpec.Stage();
        buildStage.setName("镜像构建");
        buildStage.setJobs(new LinkedHashMap<>());
        PipelineSpec.Job buildJob = job("构建任务", "build_step");
        buildJob.setNeeds(List.of("unit_test_job", "unit_test_job2"));
        buildStage.getJobs().put("build_job", buildJob);
        spec.getStages().put("build_stage", buildStage);

        return spec;
    }

    private PipelineSpec.Job job(String name, String stepId) {
        PipelineSpec.Job job = new PipelineSpec.Job();
        job.setName(name);
        PipelineSpec.RunsOn runsOn = new PipelineSpec.RunsOn();
        runsOn.setGroup("local-docker/default");
        runsOn.setContainer("eclipse-temurin:17");
        job.setRunsOn(runsOn);
        job.setSteps(new LinkedHashMap<>());
        PipelineSpec.Step step = new PipelineSpec.Step();
        step.setName(stepId);
        step.setStep(PipelineNodeRegistryServiceImpl.TYPE_COMMAND);
        step.setWith(Map.of("run", "echo ok"));
        job.getSteps().put(stepId, step);
        return job;
    }

    private PipelineWorkspace workspace() {
        return PipelineWorkspace.builder()
                .runWorkspace(Path.of("/tmp/workspace"))
                .cacheWorkspace(Path.of("/tmp/cache"))
                .cacheKey("definition-1")
                .cacheMounts(List.of())
                .build();
    }

    private void mockJobMapper() {
        doAnswer(invocation -> {
            PipelineRunJobDO jobRun = invocation.getArgument(0);
            synchronized (jobStore) {
                jobRun.setId((long) jobStore.size() + 1);
                jobStore.put(jobRun.getJobId(), jobRun);
            }
            return 1;
        }).when(pipelineRunJobMapper).insert(any(PipelineRunJobDO.class));
        when(pipelineRunJobMapper.selectListByPipelineRunId(anyLong())).thenAnswer(invocation -> {
            synchronized (jobStore) {
                return new ArrayList<>(jobStore.values());
            }
        });
        when(pipelineRunJobMapper.selectListByPipelineRunIdAndStatuses(anyLong(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<String> statuses = invocation.getArgument(1);
            synchronized (jobStore) {
                return jobStore.values().stream().filter(job -> statuses.contains(job.getStatus())).toList();
            }
        });
        when(pipelineRunJobMapper.selectByPipelineRunIdAndJobId(anyLong(), any())).thenAnswer(invocation -> {
            synchronized (jobStore) {
                return jobStore.get(invocation.getArgument(1));
            }
        });
        doAnswer(invocation -> {
            PipelineRunJobDO jobRun = invocation.getArgument(0);
            synchronized (jobStore) {
                jobStore.put(jobRun.getJobId(), jobRun);
            }
            return 1;
        }).when(pipelineRunJobMapper).updateById(any(PipelineRunJobDO.class));
    }

}
