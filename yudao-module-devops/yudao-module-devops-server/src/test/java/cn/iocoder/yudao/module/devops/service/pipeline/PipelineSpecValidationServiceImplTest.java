package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationRespVO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
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

        assertTrue(validation.getValid());
        assertTrue(validation.getErrors().isEmpty());
    }

    @Test
    public void testValidate_yamlSuccess() {
        String yaml = """
                sources:
                  my_repo:
                    type: gitSample
                    name: JAVA示例代码源
                    endpoint: https://example.com/group/repo.git
                    branch: master
                stages:
                  java_build_stage:
                    name: Java 构建上传
                    jobs:
                      java_build_job:
                        name: Java 构建上传
                        runsOn:
                          group: public/cn-beijing
                          container: registry.example.com/build/alinux3:latest
                        steps:
                          setup_java_step:
                            name: 安装Java环境
                            step: SetupJava
                            with:
                              jdkVersion: "1.8"
                              mavenVersion: "3.5.2"
                          command_step:
                            name: 执行命令
                            step: Command
                            with:
                              run: |
                                mvn -B clean package -Dmaven.test.skip=true
                """;

        PipelineValidationRespVO validation = validationService.validate(yaml);
        PipelineSpec spec = validationService.parseSpec(yaml, new PipelineValidationRespVO());
        List<PipelineSpec.ExecutableStep> steps = validationService.sortExecutableSteps(spec);

        assertTrue(validation.getValid());
        assertEquals(2, steps.size());
        assertEquals("setup_java_step", steps.get(0).getStepId());
        assertEquals(PipelineNodeRegistryServiceImpl.TYPE_SETUP_JAVA, steps.get(0).getStep());
        assertEquals("command_step", steps.get(1).getStepId());
        assertEquals(PipelineNodeRegistryServiceImpl.TYPE_COMMAND, steps.get(1).getStep());
        assertEquals("mvn -B clean package -Dmaven.test.skip=true\n", steps.get(1).getWith().get("run"));
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
    public void testValidate_approval() {
        PipelineSpec spec = buildValidSpec();
        spec.getStages().get("test_stage").getJobs().get("test_job").getSteps()
                .put("approval", step(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL,
                        Map.of("processDefinitionKey", "devops_deploy_approval")));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertTrue(validation.getValid());
    }

    @Test
    public void testValidate_approvalProcessDefinitionKeyRequired() {
        PipelineSpec spec = buildValidSpec();
        spec.getStages().get("test_stage").getJobs().get("test_job").getSteps()
                .put("approval", step(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL, Map.of()));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "approval".equals(error.getNodeId())
                && "with.processDefinitionKey".equals(error.getField())));
    }

    @Test
    public void testSortExecutableNodes() {
        PipelineSpec spec = buildValidSpec();

        List<PipelineSpec.ExecutableStep> sorted = validationService.sortExecutableSteps(spec);

        assertEquals(2, sorted.size());
        assertEquals("setup_java", sorted.get(0).getStepId());
        assertEquals("command", sorted.get(1).getStepId());
    }

    @Test
    public void testValidate_duplicateStepId() {
        PipelineSpec spec = buildValidSpec();
        PipelineSpec.Stage stage = new PipelineSpec.Stage();
        stage.setName("发布");
        PipelineSpec.Job job = new PipelineSpec.Job();
        job.setName("发布任务");
        job.setSteps(new LinkedHashMap<>());
        job.getSteps().put("command", step(PipelineNodeRegistryServiceImpl.TYPE_COMMAND, Map.of("run", "echo dup")));
        stage.setJobs(Map.of("deploy_job", job));
        spec.getStages().put("deploy_stage", stage);

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "STEP_ID_DUPLICATE".equals(error.getCode())));
    }

    private PipelineSpec buildValidSpec() {
        PipelineSpec spec = new PipelineSpec();
        PipelineSpec.Source source = new PipelineSpec.Source();
        source.setType("gitSample");
        source.setName("示例代码源");
        source.setEndpoint("https://example.com/group/repo.git");
        source.setBranch("master");
        spec.setSources(Map.of("my_repo", source));

        PipelineSpec.Stage stage = new PipelineSpec.Stage();
        stage.setName("测试");
        PipelineSpec.Job job = new PipelineSpec.Job();
        job.setName("Java 构建");
        PipelineSpec.RunsOn runsOn = new PipelineSpec.RunsOn();
        runsOn.setGroup("public/cn-beijing");
        runsOn.setContainer("registry.example.com/build/alinux3:latest");
        job.setRunsOn(runsOn);
        job.setSteps(new LinkedHashMap<>());
        job.getSteps().put("setup_java", step(PipelineNodeRegistryServiceImpl.TYPE_SETUP_JAVA,
                Map.of("jdkVersion", "17")));
        job.getSteps().put("command", step(PipelineNodeRegistryServiceImpl.TYPE_COMMAND,
                Map.of("run", "echo hello")));
        stage.setJobs(Map.of("test_job", job));
        spec.setStages(new LinkedHashMap<>());
        spec.getStages().put("test_stage", stage);
        return spec;
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
