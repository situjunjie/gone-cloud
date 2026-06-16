package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationRespVO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PipelineSpecValidationServiceImpl} 的单元测试。
 */
public class PipelineSpecValidationServiceImplTest {

    private PipelineSpecValidationServiceImpl validationService;

    @BeforeEach
    public void setUp() {
        validationService = new PipelineSpecValidationServiceImpl();
        ReflectionTestUtils.setField(validationService, "pipelineNodeRegistryService",
                new PipelineNodeRegistryServiceImpl());
    }

    @Test
    public void testValidate_success() {
        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(buildValidSpec()));

        assertTrue(validation.getValid(), JsonUtils.toJsonString(validation.getErrors()));
        assertTrue(validation.getErrors().isEmpty());
    }

    @Test
    public void testValidate_yamlSuccess() {
        String yaml = """
                sources:
                  my_repo:
                    type: gitlab
                    name: 示例代码源
                    endpoint: https://example.com/group/repo.git
                    branch: master
                stages:
                  test_stage:
                    name: 测试
                    jobs:
                      test_job:
                        name: 单元测试
                        runsOn:
                          group: local-docker/default
                          container: eclipse-temurin:17
                        steps:
                          command_step:
                            name: 执行命令
                            step: Command
                            with:
                              run: |
                                mvn -B test
                      build_job:
                        name: 构建
                        needs: test_job
                        runsOn:
                          group: local-docker/default
                          container: eclipse-temurin:17
                        steps:
                          build_step:
                            name: 构建命令
                            step: Command
                            with:
                              run: mvn -B package
                """;

        PipelineValidationRespVO validation = validationService.validate(yaml);
        PipelineSpec spec = validationService.parseSpec(yaml, new PipelineValidationRespVO());
        PipelineSpec.ExecutableGraph graph = spec.toExecutableGraph();

        assertTrue(validation.getValid(), JsonUtils.toJsonString(validation.getErrors()));
        assertEquals(2, graph.getJobs().size());
        assertEquals("test_job", graph.getJobs().get(1).getNeeds().get(0));
        assertEquals("command_step", graph.getSteps().get(0).getStepId());
        assertEquals("mvn -B test\n", graph.getSteps().get(0).getWith().get("run"));
    }

    @Test
    public void testValidate_commandRunRequired() {
        PipelineSpec spec = buildValidSpec();
        spec.getStages().get("test_stage").getJobs().get("test_job").getSteps()
                .put("empty_command", step(PipelineNodeRegistryServiceImpl.TYPE_COMMAND, Map.of()));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "empty_command".equals(error.getNodeId())
                && "with.run".equals(error.getField())));
    }

    @Test
    public void testValidate_unsupportedStep() {
        PipelineSpec spec = buildValidSpec();
        spec.getStages().get("test_stage").getJobs().get("test_job").getSteps()
                .put("setup_java", step(PipelineNodeRegistryServiceImpl.TYPE_SETUP_JAVA, Map.of("jdkVersion", "17")));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "STEP_TYPE_UNSUPPORTED".equals(error.getCode())));
    }

    @Test
    public void testValidate_codeMergeSuccess() {
        PipelineSpec spec = buildValidSpec();
        PipelineSpec.Job job = new PipelineSpec.Job();
        job.setName("代码合并");
        job.setRunsOn(null);
        job.setSteps(new LinkedHashMap<>());
        job.getSteps().put("code_merge_step", step(PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE, Map.of(
                "baseBranch", "${SOURCE_BRANCH}",
                "targetBranch", "${BRANCH_NAME}",
                "branches", java.util.List.of("feature/a", "feature/b"),
                "pushOnSuccess", true)));
        spec.getStages().get("test_stage").getJobs().put("code_merge_job", job);

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertTrue(validation.getValid(), JsonUtils.toJsonString(validation.getErrors()));
    }

    @Test
    public void testValidate_codeMergeBaseBranchRequired() {
        PipelineSpec spec = buildValidSpec();
        PipelineSpec.Job job = new PipelineSpec.Job();
        job.setName("代码合并");
        job.setSteps(new LinkedHashMap<>());
        job.getSteps().put("code_merge_step", step(PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE, Map.of(
                "targetBranch", "${BRANCH_NAME}",
                "branchesFromSubmit", true)));
        spec.getStages().get("test_stage").getJobs().put("code_merge_job", job);

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "code_merge_step".equals(error.getNodeId())
                && "with.baseBranch".equals(error.getField())));
    }

    @Test
    public void testValidate_approvalSuccess_withoutRunsOn() {
        PipelineSpec spec = buildValidSpec();
        PipelineSpec.Job job = new PipelineSpec.Job();
        job.setName("发布审批");
        job.setSteps(new LinkedHashMap<>());
        job.getSteps().put("approval_step", step(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL,
                Map.of("processDefinitionKey", "devops_deploy_approval")));
        spec.getStages().get("test_stage").getJobs().put("approval_job", job);

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertTrue(validation.getValid(), JsonUtils.toJsonString(validation.getErrors()));
    }

    @Test
    public void testValidate_approvalProcessDefinitionKeyRequired() {
        PipelineSpec spec = buildValidSpec();
        PipelineSpec.Job job = new PipelineSpec.Job();
        job.setName("发布审批");
        job.setSteps(new LinkedHashMap<>());
        job.getSteps().put("approval_step", step(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL, Map.of()));
        spec.getStages().get("test_stage").getJobs().put("approval_job", job);

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "approval_step".equals(error.getNodeId())
                && "with.processDefinitionKey".equals(error.getField())));
    }

    @Test
    public void testValidate_codeMergeBranchesTypeInvalid() {
        PipelineSpec spec = buildValidSpec();
        PipelineSpec.Job job = new PipelineSpec.Job();
        job.setName("代码合并");
        job.setSteps(new LinkedHashMap<>());
        job.getSteps().put("code_merge_step", step(PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE, Map.of(
                "baseBranch", "${SOURCE_BRANCH}",
                "targetBranch", "${BRANCH_NAME}",
                "branches", "feature/a")));
        spec.getStages().get("test_stage").getJobs().put("code_merge_job", job);

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "code_merge_step".equals(error.getNodeId())
                && "with.branches".equals(error.getField())
                && "PARAM_TYPE_INVALID".equals(error.getCode())));
    }

    @Test
    public void testValidate_sourceCountUnsupported() {
        PipelineSpec spec = buildValidSpec();
        PipelineSpec.Source source = new PipelineSpec.Source();
        source.setType("gitlab");
        source.setName("另一个代码源");
        source.setEndpoint("https://example.com/group/another.git");
        source.setBranch("master");
        spec.getSources().put("another_repo", source);

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "SOURCE_COUNT_UNSUPPORTED".equals(error.getCode())));
    }

    @Test
    public void testValidate_sourceTypeUnsupported() {
        PipelineSpec spec = buildValidSpec();
        spec.getSources().get("my_repo").setType("git");

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "SOURCE_TYPE_UNSUPPORTED".equals(error.getCode())));
    }

    @Test
    public void testValidate_duplicateStepId() {
        PipelineSpec spec = buildValidSpec();
        PipelineSpec.Stage stage = new PipelineSpec.Stage();
        stage.setName("发布");
        PipelineSpec.Job job = buildJob("echo dup");
        job.getSteps().clear();
        job.getSteps().put("command", step(PipelineNodeRegistryServiceImpl.TYPE_COMMAND, Map.of("run", "echo dup")));
        stage.setJobs(Map.of("deploy_job", job));
        spec.getStages().put("deploy_stage", stage);

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "STEP_ID_DUPLICATE".equals(error.getCode())));
    }

    @Test
    public void testValidate_duplicateJobId() {
        PipelineSpec spec = buildValidSpec();
        PipelineSpec.Stage stage = new PipelineSpec.Stage();
        stage.setName("发布");
        stage.setJobs(Map.of("test_job", buildJob("echo deploy")));
        spec.getStages().put("deploy_stage", stage);

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "JOB_ID_DUPLICATE".equals(error.getCode())));
    }

    @Test
    public void testValidate_needsNotFound() {
        PipelineSpec spec = buildValidSpec();
        spec.getStages().get("test_stage").getJobs().get("test_job").setNeeds("missing_job");

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "JOB_NEEDS_NOT_FOUND".equals(error.getCode())));
    }

    @Test
    public void testValidate_needsSelf() {
        PipelineSpec spec = buildValidSpec();
        spec.getStages().get("test_stage").getJobs().get("test_job").setNeeds("test_job");

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "JOB_NEEDS_SELF".equals(error.getCode())));
    }

    @Test
    public void testValidate_needsCycle() {
        PipelineSpec spec = buildValidSpec();
        PipelineSpec.Job testJob = spec.getStages().get("test_stage").getJobs().get("test_job");
        testJob.setNeeds("build_job");
        spec.getStages().get("test_stage").getJobs().put("build_job", buildJob("echo build"));
        spec.getStages().get("test_stage").getJobs().get("build_job").setNeeds("test_job");

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "JOB_NEEDS_CYCLE".equals(error.getCode())));
    }

    private PipelineSpec buildValidSpec() {
        PipelineSpec spec = new PipelineSpec();
        PipelineSpec.Source source = new PipelineSpec.Source();
        source.setType("gitlab");
        source.setName("示例代码源");
        source.setEndpoint("https://example.com/group/repo.git");
        source.setBranch("master");
        spec.setSources(new LinkedHashMap<>());
        spec.getSources().put("my_repo", source);

        PipelineSpec.Stage stage = new PipelineSpec.Stage();
        stage.setName("测试");
        stage.setJobs(new LinkedHashMap<>());
        stage.getJobs().put("test_job", buildJob("echo hello"));
        spec.setStages(new LinkedHashMap<>());
        spec.getStages().put("test_stage", stage);
        return spec;
    }

    private PipelineSpec.Job buildJob(String command) {
        PipelineSpec.Job job = new PipelineSpec.Job();
        job.setName("任务");
        PipelineSpec.RunsOn runsOn = new PipelineSpec.RunsOn();
        runsOn.setGroup("local-docker/default");
        runsOn.setContainer("eclipse-temurin:17");
        job.setRunsOn(runsOn);
        job.setSteps(new LinkedHashMap<>());
        job.getSteps().put("command", step(PipelineNodeRegistryServiceImpl.TYPE_COMMAND, Map.of("run", command)));
        return job;
    }

    private PipelineSpec.Step step(String type, Map<String, Object> with) {
        PipelineSpec.Step step = new PipelineSpec.Step();
        step.setStep(type);
        step.setName(type);
        step.setEnabled(true);
        step.setWith(with);
        return step;
    }

}
