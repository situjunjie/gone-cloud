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
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntimeManager;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private PipelineSourceWorkspacePreparer pipelineSourceWorkspacePreparer;

    @InjectMocks
    private PipelineExecutionEngine engine;

    private final Map<String, PipelineRunJobDO> jobStore = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockJobMapper();
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
        when(pipelineWorkspaceService.createWorkspace(any(), any())).thenReturn(Path.of("/tmp/workspace"));
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
        verify(pipelineSourceWorkspacePreparer, org.mockito.Mockito.times(2))
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
        when(pipelineWorkspaceService.createWorkspace(any(), any())).thenReturn(Path.of("/tmp/workspace"));
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

    private void mockJobMapper() {
        doAnswer(invocation -> {
            PipelineRunJobDO jobRun = invocation.getArgument(0);
            jobRun.setId((long) jobStore.size() + 1);
            jobStore.put(jobRun.getJobId(), jobRun);
            return 1;
        }).when(pipelineRunJobMapper).insert(any(PipelineRunJobDO.class));
        when(pipelineRunJobMapper.selectListByPipelineRunId(anyLong())).thenAnswer(invocation -> new ArrayList<>(jobStore.values()));
        when(pipelineRunJobMapper.selectListByPipelineRunIdAndStatuses(anyLong(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<String> statuses = invocation.getArgument(1);
            return jobStore.values().stream().filter(job -> statuses.contains(job.getStatus())).toList();
        });
        when(pipelineRunJobMapper.selectByPipelineRunIdAndJobId(anyLong(), any())).thenAnswer(invocation ->
                jobStore.get(invocation.getArgument(1)));
        doAnswer(invocation -> {
            PipelineRunJobDO jobRun = invocation.getArgument(0);
            jobStore.put(jobRun.getJobId(), jobRun);
            return 1;
        }).when(pipelineRunJobMapper).updateById(any(PipelineRunJobDO.class));
    }

}
