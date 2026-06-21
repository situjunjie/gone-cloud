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
    public void testValidate_yamlRejectUnknownField() {
        String yaml = """
                stages:
                  build_stage:
                    name: 镜像构建
                    jobs:
                      build_job:
                        name: 构建任务
                        steps:
                          docker_build:
                            name: 镜像构建
                            step: PrivateRegistryDockerBuild
                            with:
                              artifact: my_image
                              image: registry.example.com/ns/demo:1.0
                              certificate:
                                type: usernamePassword
                                username: robot
                                password: secret
                              dockerfilePath: Dockerfile
                    deploy_stage:
                      name: K8s 部署
                      jobs:
                        deploy_job:
                          name: 部署到 K8s
                          steps:
                            k8s_deploy:
                              name: 部署 demo
                              step: K8sDeploy
                              with:
                                deployMode: RAW_MANIFEST
                                manifestYaml: "%s"
                                containerName: app
                                image: registry.example.com/ns/demo:1.0
                """.formatted(deploymentManifestYaml().replace("\n", "\\n"));

        PipelineValidationRespVO validation = validationService.validate(yaml);

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "SPEC_INVALID".equals(error.getCode())));
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
    public void testValidate_privateRegistryDockerBuildSuccess() {
        PipelineSpec spec = buildValidSpec();
        spec.getStages().get("test_stage").getJobs().get("test_job").getSteps()
                .put("docker_build", step(PipelineNodeRegistryServiceImpl.TYPE_PRIVATE_REGISTRY_DOCKER_BUILD,
                        privateRegistryDockerBuildParams(Map.of(
                                "type", "usernamePassword",
                                "username", "robot",
                                "password", "secret"))));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertTrue(validation.getValid(), JsonUtils.toJsonString(validation.getErrors()));
    }

    @Test
    public void testValidate_privateRegistryDockerBuildRejectServiceConnection() {
        PipelineSpec spec = buildValidSpec();
        spec.getStages().get("test_stage").getJobs().get("test_job").getSteps()
                .put("docker_build", step(PipelineNodeRegistryServiceImpl.TYPE_PRIVATE_REGISTRY_DOCKER_BUILD,
                        privateRegistryDockerBuildParams(Map.of(
                                "type", "serviceConnection",
                                "serviceConnection", "registry-1"))));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "docker_build".equals(error.getNodeId())
                && "with.certificate.type".equals(error.getField())
                && "PARAM_VALUE_UNSUPPORTED".equals(error.getCode())));
    }

    @Test
    public void testValidate_privateRegistryDockerBuildPasswordRequired() {
        PipelineSpec spec = buildValidSpec();
        spec.getStages().get("test_stage").getJobs().get("test_job").getSteps()
                .put("docker_build", step(PipelineNodeRegistryServiceImpl.TYPE_PRIVATE_REGISTRY_DOCKER_BUILD,
                        privateRegistryDockerBuildParams(Map.of(
                                "type", "usernamePassword",
                                "username", "robot"))));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "docker_build".equals(error.getNodeId())
                && "with.certificate.password".equals(error.getField())));
    }

    @Test
    public void testValidate_privateRegistryDockerBuildSuccess_withoutRunsOn() {
        PipelineSpec spec = buildValidSpec();
        PipelineSpec.Job job = new PipelineSpec.Job();
        job.setName("镜像构建");
        job.setSteps(new LinkedHashMap<>());
        job.getSteps().put("docker_build", step(PipelineNodeRegistryServiceImpl.TYPE_PRIVATE_REGISTRY_DOCKER_BUILD,
                privateRegistryDockerBuildParams(Map.of(
                        "type", "usernamePassword",
                        "username", "robot",
                        "password", "secret"))));
        spec.getStages().get("test_stage").getJobs().put("docker_build_job", job);

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertTrue(validation.getValid(), JsonUtils.toJsonString(validation.getErrors()));
    }

    @Test
    public void testValidate_dockerImageExportObjectStorageSuccess() {
        PipelineSpec spec = buildValidSpec();
        spec.getStages().get("test_stage").getJobs().get("test_job").getSteps()
                .put("image_export", step(PipelineNodeRegistryServiceImpl.TYPE_DOCKER_IMAGE_EXPORT_OBJECT_STORAGE,
                        dockerImageExportObjectStorageParams()));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertTrue(validation.getValid(), JsonUtils.toJsonString(validation.getErrors()));
    }

    @Test
    public void testValidate_dockerImageExportObjectStorageAllowPlaceholders() {
        PipelineSpec spec = buildValidSpec();
        Map<String, Object> params = dockerImageExportObjectStorageParams();
        params.put("image", "registry.cn-hangzhou.aliyuncs.com/ns/demo:${COMMIT_SHA}");
        params.put("archiveFormat", "${ARCHIVE_FORMAT}");
        params.put("compression", "${COMPRESSION}");
        params.put("outputFileName", "${OUTPUT_FILE_NAME}");
        params.put("registryTlsVerify", "${TLS_VERIFY}");
        params.put("overwrite", "${STORAGE_OVERWRITE}");
        Map<String, Object> registryCertificate = new LinkedHashMap<>();
        registryCertificate.put("type", "${REGISTRY_CERT_TYPE}");
        registryCertificate.put("username", "robot");
        registryCertificate.put("password", "secret-pass");
        params.put("registryCertificate", registryCertificate);
        @SuppressWarnings("unchecked")
        Map<String, Object> storage = (Map<String, Object>) params.get("storage");
        storage.put("type", "${STORAGE_TYPE}");
        storage.put("path", "${STORAGE_PATH}");
        storage.put("region", "${STORAGE_REGION}");
        storage.put("forcePathStyle", "${FORCE_PATH_STYLE}");
        Map<String, Object> storageCertificate = new LinkedHashMap<>();
        storageCertificate.put("type", "${STORAGE_CERT_TYPE}");
        storageCertificate.put("accessKeyId", "ak");
        storageCertificate.put("accessKeySecret", "secret-ak");
        storage.put("certificate", storageCertificate);
        spec.getStages().get("test_stage").getJobs().get("test_job").getSteps()
                .put("image_export", step(PipelineNodeRegistryServiceImpl.TYPE_DOCKER_IMAGE_EXPORT_OBJECT_STORAGE, params));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertTrue(validation.getValid(), JsonUtils.toJsonString(validation.getErrors()));
    }

    @Test
    public void testValidate_dockerImageExportObjectStorageRejectInvalidStoragePath() {
        PipelineSpec spec = buildValidSpec();
        Map<String, Object> params = dockerImageExportObjectStorageParams();
        @SuppressWarnings("unchecked")
        Map<String, Object> storage = (Map<String, Object>) params.get("storage");
        storage.put("path", "release-bucket/images/demo.tar");
        spec.getStages().get("test_stage").getJobs().get("test_job").getSteps()
                .put("image_export", step(PipelineNodeRegistryServiceImpl.TYPE_DOCKER_IMAGE_EXPORT_OBJECT_STORAGE, params));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "image_export".equals(error.getNodeId())
                && "with.storage.path".equals(error.getField())
                && "PARAM_VALUE_UNSUPPORTED".equals(error.getCode())));
    }

    @Test
    public void testValidate_dockerImageExportObjectStorageRejectCompression() {
        PipelineSpec spec = buildValidSpec();
        Map<String, Object> params = dockerImageExportObjectStorageParams();
        params.put("compression", "xz");
        spec.getStages().get("test_stage").getJobs().get("test_job").getSteps()
                .put("image_export", step(PipelineNodeRegistryServiceImpl.TYPE_DOCKER_IMAGE_EXPORT_OBJECT_STORAGE, params));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "image_export".equals(error.getNodeId())
                && "with.compression".equals(error.getField())
                && "PARAM_VALUE_UNSUPPORTED".equals(error.getCode())));
    }

    @Test
    public void testValidate_dockerImageExportObjectStorageRejectOutputPathTraversal() {
        PipelineSpec spec = buildValidSpec();
        Map<String, Object> params = dockerImageExportObjectStorageParams();
        params.put("outputFileName", "../demo.tar");
        spec.getStages().get("test_stage").getJobs().get("test_job").getSteps()
                .put("image_export", step(PipelineNodeRegistryServiceImpl.TYPE_DOCKER_IMAGE_EXPORT_OBJECT_STORAGE, params));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "image_export".equals(error.getNodeId())
                && "with.outputFileName".equals(error.getField())
                && "PARAM_VALUE_UNSUPPORTED".equals(error.getCode())));
    }

    @Test
    public void testValidate_dockerImageArchiveImportSuccess() {
        PipelineSpec spec = buildValidSpec();
        spec.getStages().get("test_stage").getJobs().get("test_job").getSteps()
                .put("image_import", step(PipelineNodeRegistryServiceImpl.TYPE_DOCKER_IMAGE_ARCHIVE_IMPORT,
                        dockerImageArchiveImportParams()));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertTrue(validation.getValid(), JsonUtils.toJsonString(validation.getErrors()));
    }

    @Test
    public void testValidate_dockerImageArchiveImportAllowPlaceholders() {
        PipelineSpec spec = buildValidSpec();
        Map<String, Object> params = dockerImageArchiveImportParams();
        params.put("fileUrl", "${FILE_URL}");
        params.put("image", "registry.cn-hangzhou.aliyuncs.com/ns/demo:${runId}");
        params.put("archiveFormat", "${ARCHIVE_FORMAT}");
        params.put("compression", "${COMPRESSION}");
        params.put("registryTlsVerify", "${TLS_VERIFY}");
        Map<String, Object> certificate = new LinkedHashMap<>();
        certificate.put("type", "${REGISTRY_CERT_TYPE}");
        certificate.put("username", "robot");
        certificate.put("password", "secret-pass");
        params.put("certificate", certificate);
        spec.getStages().get("test_stage").getJobs().get("test_job").getSteps()
                .put("image_import", step(PipelineNodeRegistryServiceImpl.TYPE_DOCKER_IMAGE_ARCHIVE_IMPORT, params));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertTrue(validation.getValid(), JsonUtils.toJsonString(validation.getErrors()));
    }

    @Test
    public void testValidate_dockerImageArchiveImportRejectMissingFileUrl() {
        PipelineSpec spec = buildValidSpec();
        Map<String, Object> params = dockerImageArchiveImportParams();
        params.remove("fileUrl");
        spec.getStages().get("test_stage").getJobs().get("test_job").getSteps()
                .put("image_import", step(PipelineNodeRegistryServiceImpl.TYPE_DOCKER_IMAGE_ARCHIVE_IMPORT, params));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "image_import".equals(error.getNodeId())
                && "with.fileUrl".equals(error.getField())
                && "PARAM_REQUIRED".equals(error.getCode())));
    }

    @Test
    public void testValidate_dockerImageArchiveImportRejectCompression() {
        PipelineSpec spec = buildValidSpec();
        Map<String, Object> params = dockerImageArchiveImportParams();
        params.put("compression", "xz");
        spec.getStages().get("test_stage").getJobs().get("test_job").getSteps()
                .put("image_import", step(PipelineNodeRegistryServiceImpl.TYPE_DOCKER_IMAGE_ARCHIVE_IMPORT, params));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "image_import".equals(error.getNodeId())
                && "with.compression".equals(error.getField())
                && "PARAM_VALUE_UNSUPPORTED".equals(error.getCode())));
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
    public void testValidate_k8sDeploySuccess_withoutRunsOn() {
        PipelineSpec spec = buildValidSpec();
        PipelineSpec.Job job = new PipelineSpec.Job();
        job.setName("K8s 部署");
        job.setSteps(new LinkedHashMap<>());
        job.getSteps().put("k8s_deploy", step(PipelineNodeRegistryServiceImpl.TYPE_K8S_DEPLOY, Map.of(
                "deployMode", "RAW_MANIFEST",
                "manifestYaml", deploymentManifestYaml(),
                "containerName", "app",
                "image", "registry.example.com/gone-api:${COMMIT_SHA}",
                "replicas", 2)));
        spec.getStages().get("test_stage").getJobs().put("deploy_job", job);

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertTrue(validation.getValid(), JsonUtils.toJsonString(validation.getErrors()));
    }

    @Test
    public void testValidate_k8sImageUpgradeSuccess_withoutRunsOn() {
        PipelineSpec spec = buildValidSpec();
        PipelineSpec.Job job = new PipelineSpec.Job();
        job.setName("K8s 镜像版本升级");
        job.setSteps(new LinkedHashMap<>());
        job.getSteps().put("k8s_upgrade", step(PipelineNodeRegistryServiceImpl.TYPE_K8S_IMAGE_UPGRADE, Map.of(
                "workloadKind", "Deployment",
                "workloadName", "gone-api",
                "containerName", "app",
                "image", "registry.example.com/gone-api:${COMMIT_SHA}",
                "rolloutTimeoutSeconds", 300)));
        spec.getStages().get("test_stage").getJobs().put("upgrade_job", job);

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertTrue(validation.getValid(), JsonUtils.toJsonString(validation.getErrors()));
    }

    @Test
    public void testValidate_k8sImageUpgradeWorkloadNameRequired() {
        PipelineSpec spec = buildValidSpec();
        PipelineSpec.Job job = new PipelineSpec.Job();
        job.setName("K8s 镜像版本升级");
        job.setSteps(new LinkedHashMap<>());
        job.getSteps().put("k8s_upgrade", step(PipelineNodeRegistryServiceImpl.TYPE_K8S_IMAGE_UPGRADE, Map.of(
                "workloadKind", "Deployment",
                "containerName", "app",
                "image", "registry.example.com/gone-api:${COMMIT_SHA}")));
        spec.getStages().get("test_stage").getJobs().put("upgrade_job", job);

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "k8s_upgrade".equals(error.getNodeId())
                && "with.workloadName".equals(error.getField())));
    }

    @Test
    public void testValidate_k8sImageUpgradeUnsupportedKind() {
        PipelineSpec spec = buildValidSpec();
        PipelineSpec.Job job = new PipelineSpec.Job();
        job.setName("K8s 镜像版本升级");
        job.setSteps(new LinkedHashMap<>());
        job.getSteps().put("k8s_upgrade", step(PipelineNodeRegistryServiceImpl.TYPE_K8S_IMAGE_UPGRADE, Map.of(
                "workloadKind", "StatefulSet",
                "workloadName", "gone-api",
                "containerName", "app",
                "image", "registry.example.com/gone-api:${COMMIT_SHA}")));
        spec.getStages().get("test_stage").getJobs().put("upgrade_job", job);

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "k8s_upgrade".equals(error.getNodeId())
                && "with.workloadKind".equals(error.getField())
                && "PARAM_VALUE_UNSUPPORTED".equals(error.getCode())));
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

    private Map<String, Object> privateRegistryDockerBuildParams(Map<String, Object> certificate) {
        Map<String, Object> with = new LinkedHashMap<>();
        with.put("artifact", "my_image");
        with.put("image", "registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0");
        with.put("certificate", certificate);
        with.put("dockerfilePath", "Dockerfile");
        with.put("variables", java.util.List.of(Map.of("key", "PROFILE", "value", "prod")));
        return with;
    }

    private Map<String, Object> dockerImageExportObjectStorageParams() {
        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("type", "s3");
        storage.put("endpoint", "https://oss-cn-hangzhou.aliyuncs.com");
        storage.put("path", "s3://release-bucket/images/demo-1.0.oci.tar");
        storage.put("region", "cn-hangzhou");
        storage.put("forcePathStyle", true);
        storage.put("certificate", Map.of(
                "type", "accessKey",
                "accessKeyId", "ak",
                "accessKeySecret", "secret-ak"));
        Map<String, Object> with = new LinkedHashMap<>();
        with.put("image", "registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0");
        with.put("archiveFormat", "oci-archive");
        with.put("compression", "none");
        with.put("outputFileName", "demo-1.0.oci.tar");
        with.put("registryCertificate", Map.of(
                "type", "usernamePassword",
                "username", "robot",
                "password", "secret-pass"));
        with.put("storage", storage);
        with.put("overwrite", false);
        return with;
    }

    private Map<String, Object> dockerImageArchiveImportParams() {
        Map<String, Object> with = new LinkedHashMap<>();
        with.put("fileUrl", "https://example.com/images/demo.oci.tar.zst");
        with.put("image", "registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0");
        with.put("archiveFormat", "oci-archive");
        with.put("compression", "zstd");
        with.put("registryTlsVerify", true);
        with.put("certificate", Map.of(
                "type", "usernamePassword",
                "username", "robot",
                "password", "secret-pass"));
        return with;
    }

    private String deploymentManifestYaml() {
        return """
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: gone-api
                spec:
                  selector:
                    matchLabels:
                      app: gone-api
                  template:
                    metadata:
                      labels:
                        app: gone-api
                    spec:
                      containers:
                        - name: app
                          image: ${IMAGE}
                """;
    }

}
