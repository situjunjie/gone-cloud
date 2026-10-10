package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.config.DevopsConfig;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesClientFactory;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesEnvironmentConfig;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.environment.EnvironmentService;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.PodResource;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.devops.framework.pipeline.runtime.ShellQuoting.quote;

/**
 * 基于 Kubernetes 的流水线任务运行时管理器。
 *
 * <p>在 K8s 集群中创建 Pod 用于执行构建任务，支持：
 * <ul>
 *     <li>工作空间持久化（共享 PVC / 动态 PVC / EmptyDir）</li>
 *     <li>资源配额管理</li>
 *     <li>缓存挂载</li>
 *     <li>Pod 生命周期管理</li>
 * </ul>
 */
@Slf4j
@Component
public class K8sPipelineJobRuntimeManager implements PipelineJobRuntimeManager {

    private static final String EXECUTOR_GROUP_PREFIX = "kubernetes/";
    private static final String CONTAINER_WORKSPACE = PipelineWorkspaceConstants.CONTAINER_WORKSPACE;
    private static final int POD_READY_TIMEOUT_SECONDS = 300;

    @Resource
    private KubernetesClientFactory kubernetesClientFactory;

    @Resource
    private EnvironmentService environmentService;

    @Resource
    private DevopsConfig devopsConfig;

    @Override
    public PipelineJobRuntime createRuntime(PipelineRunDO run, PipelineSpec.ExecutableJob job, PipelineWorkspace workspace) {
        // 1. 解析 runs-on 配置
        PipelineSpec.RunsOn runsOn = job.getRunsOn();
        String group = StrUtil.blankToDefault(runsOn == null ? null : runsOn.getGroup(), "kubernetes/default");
        String image = runsOn == null ? null : runsOn.getContainer();

        if (!isK8sExecutorGroup(group)) {
            throw new IllegalStateException("Unsupported executor group: " + group);
        }

        if (StrUtil.isBlank(image)) {
            image = devopsConfig.getBuild().getCluster().getDefaultImage();
        }
        validateCluster(image);

        // 2. 获取 K8s 环境配置
        Long environmentId = devopsConfig.getBuild().getCluster().getEnvironmentId();
        if (environmentId == null) {
            throw exception(DEVOPS_BUILD_CLUSTER_NOT_CONFIGURED);
        }

        EnvironmentDO environment = environmentService.getEnvironment(environmentId);
        if (environment == null) {
            throw exception(ENVIRONMENT_NOT_EXISTS);
        }

        KubernetesEnvironmentConfig envConfig = parseK8sConfig(environment);
        String namespace = devopsConfig.getBuild().getCluster().getNamespace();

        // 3. 创建 Kubernetes 客户端
        try (KubernetesClient client = kubernetesClientFactory.create(envConfig.getKubeconfig())) {
            // 4. 准备工作空间存储
            List<Volume> volumes = new ArrayList<>();
            List<VolumeMount> volumeMounts = new ArrayList<>();
            prepareWorkspaceVolume(client, namespace, run, workspace, volumes, volumeMounts);

            // 5. 准备缓存挂载
            prepareCacheVolumes(run, workspace, volumes, volumeMounts);

            // 6. 创建 Pod
            String podName = generatePodName(run, job);
            Pod pod = buildPod(podName, namespace, image, run, job, volumes, volumeMounts, workspace);

            log.info("[createRuntime][runId({}) jobId({}) 创建 K8s Pod: {}]",
                    run.getId(), job.getJobId(), podName);

            client.pods().inNamespace(namespace).resource(pod).create();

            // 7. 等待 Pod Ready
            try {
                waitPodReady(client, namespace, podName);
            } catch (RuntimeException readinessFailure) {
                try {
                    client.pods().inNamespace(namespace).withName(podName).delete();
                } catch (RuntimeException cleanupFailure) {
                    log.warn("[createRuntime][runId({}) pod({}) rollback failed]", run.getId(), podName);
                }
                throw readinessFailure;
            }

            // 8. 构造运行时对象
            return PipelineJobRuntime.builder()
                    .runtimeType("KUBERNETES")
                    .runtimeId(podName)
                    .runtimeName(podName)
                    .executorGroup(group)
                    .executorImage(image)
                    // 平台侧工作目录：日志落盘、运行记录都基于该路径，必须是平台可见的本地目录；
                    // 容器内路径由 CONTAINER_WORKSPACE / workspaceSubPath 单独描述。
                    .workspace(workspace.getRunWorkspace())
                    .cacheKey(workspace.getCacheKey())
                    .cacheMounts(workspace.getCacheMounts())
                    .metadata(buildRuntimeMetadata(namespace, environmentId, envConfig))
                    .build();

        } catch (KubernetesClientException ex) {
            log.error("[createRuntime][runId({}) jobId({}) 创建 K8s Pod 失败]",
                    run.getId(), job.getJobId(), ex);
            throw exception(DEVOPS_BUILD_K8S_POD_CREATE_FAIL, ex.getMessage());
        }
    }

    @Override
    public void destroyRuntime(PipelineJobRuntime runtime) {
        destroyRuntime(runtime, false);
    }

    @Override
    public PipelineJobRuntime restoreRuntime(PipelineRunDO run, PipelineSpec.ExecutableJob job,
                                             PipelineWorkspace workspace, String runtimeId) {
        DevopsConfig.BuildClusterConfig cluster = devopsConfig.getBuild().getCluster();
        if (StrUtil.isBlank(runtimeId)) {
            return createRuntime(run, job, workspace);
        }
        Long environmentId = cluster.getEnvironmentId();
        if (environmentId == null) throw exception(DEVOPS_BUILD_CLUSTER_NOT_CONFIGURED);
        EnvironmentDO environment = environmentService.getEnvironment(environmentId);
        if (environment == null) throw exception(ENVIRONMENT_NOT_EXISTS);
        KubernetesEnvironmentConfig config = parseK8sConfig(environment);
        try (KubernetesClient client = kubernetesClientFactory.create(config.getKubeconfig())) {
            Pod pod = client.pods().inNamespace(cluster.getNamespace()).withName(runtimeId).get();
            if (pod == null || pod.getMetadata() == null || pod.getMetadata().getLabels() == null
                    || !String.valueOf(run.getId()).equals(pod.getMetadata().getLabels().get("pipeline-run-id"))
                    || !safeLabel(job.getJobId()).equals(pod.getMetadata().getLabels().get("pipeline-job-id"))
                    || pod.getMetadata().getDeletionTimestamp() != null
                    || pod.getStatus() == null || !"Running".equals(pod.getStatus().getPhase())) {
                throw new IllegalStateException("Suspended Kubernetes build Pod is unavailable");
            }
            return PipelineJobRuntime.builder().runtimeType("KUBERNETES").runtimeId(runtimeId).runtimeName(runtimeId)
                    .executorGroup(job.getRunsOn() == null ? "kubernetes/default" : job.getRunsOn().getGroup())
                    .executorImage(StrUtil.blankToDefault(job.getRunsOn() == null ? null : job.getRunsOn().getContainer(), cluster.getDefaultImage()))
                    .workspace(workspace.getRunWorkspace()).cacheKey(workspace.getCacheKey())
                    .cacheMounts(workspace.getCacheMounts()).metadata(buildRuntimeMetadata(cluster.getNamespace(), environmentId, config)).build();
        }
    }

    @Override
    public void destroyRuntime(PipelineJobRuntime runtime, boolean failed) {
        if (runtime == null || StrUtil.isBlank(runtime.getRuntimeId())) {
            return;
        }

        String namespace = (String) runtime.getMetadata().get("namespace");
        String podName = runtime.getRuntimeId();
        KubernetesEnvironmentConfig envConfig = getEnvConfigFromRuntime(runtime);

        try (KubernetesClient client = kubernetesClientFactory.create(envConfig.getKubeconfig())) {
            DevopsConfig.PodCleanupConfig cleanupConfig = devopsConfig.getBuild().getCluster().getPodCleanup();
            String cleanupStrategy = StrUtil.blankToDefault(cleanupConfig.getStrategy(), "IMMEDIATE");

            if (failed && "RETAIN".equalsIgnoreCase(cleanupStrategy)
                    && Boolean.TRUE.equals(cleanupConfig.getRetainFailedPods())) {
                Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
                if (pod != null) {
                    client.pods().inNamespace(namespace).withName(podName).edit(p -> new PodBuilder(p)
                            .editMetadata().addToAnnotations("devops.cleanup-after", String.valueOf(System.currentTimeMillis()
                                    + Math.max(0, cleanupConfig.getRetentionMinutes()) * 60_000L)).endMetadata().build());
                }
            } else {
                deletePod(client, namespace, podName);
            }
        } catch (Exception ex) {
            log.warn("[destroyRuntime][runtimeId({}) 删除 K8s Pod 失败: {}]",
                    podName, ex.getMessage());
        }
    }

    @Override
    public void cleanupRun(PipelineRunDO run) {
        if (run == null || run.getRunStatus() == null || !Set.of(PipelineRunStatusEnum.SUCCESS.getStatus(), PipelineRunStatusEnum.FAILED.getStatus(),
                PipelineRunStatusEnum.CANCELED.getStatus()).contains(run.getRunStatus())) {
            return;
        }
        Long environmentId = devopsConfig.getBuild().getCluster().getEnvironmentId();
        if (environmentId == null) return;
        try {
            EnvironmentDO environment = environmentService.getEnvironment(environmentId);
            if (environment == null) return;
            KubernetesEnvironmentConfig config = parseK8sConfig(environment);
            String namespace = devopsConfig.getBuild().getCluster().getNamespace();
            try (KubernetesClient client = kubernetesClientFactory.create(config.getKubeconfig())) {
                String claimName = "pipeline-run-" + run.getId();
                PersistentVolumeClaim claim = client.persistentVolumeClaims().inNamespace(namespace)
                        .withName(claimName).get();
                boolean ownedClaim = ownsDynamicWorkspace(claim, String.valueOf(run.getId()));
                // Persist cleanup intent before deleting Pods: deletion is asynchronous, and
                // retained Pods may outlive this call by hours. The reaper retries the PVC later.
                if (ownedClaim) {
                    client.persistentVolumeClaims().inNamespace(namespace).withName(claimName)
                            .edit(pvc -> new PersistentVolumeClaimBuilder(pvc).editMetadata()
                                    .addToAnnotations("devops.cleanup-after", String.valueOf(System.currentTimeMillis()))
                                    .endMetadata().build());
                }
                List<Pod> pods = client.pods().inNamespace(namespace).withLabel("app", "pipeline-build")
                        .withLabel("pipeline-run-id", String.valueOf(run.getId())).list().getItems();
                for (Pod pod : pods) {
                    String until = pod.getMetadata().getAnnotations() == null ? null
                            : pod.getMetadata().getAnnotations().get("devops.cleanup-after");
                    if (until == null || !isFuture(until)) {
                        deletePod(client, namespace, pod.getMetadata().getName());
                    }
                }
                if (ownedClaim
                        && client.pods().inNamespace(namespace).withLabel("app", "pipeline-build")
                        .withLabel("pipeline-run-id", String.valueOf(run.getId())).list().getItems().isEmpty()) {
                    client.persistentVolumeClaims().inNamespace(namespace)
                            .withName(claimName).delete();
                }
            }
        } catch (Exception ex) {
            log.warn("[cleanupRun][runId({}) cleanup failed: {}]", run.getId(), ex.getMessage());
        }
    }

    private boolean isFuture(String value) {
        try { return Long.parseLong(value) > System.currentTimeMillis(); }
        catch (NumberFormatException ignored) { return false; }
    }

    static boolean ownsDynamicWorkspace(PersistentVolumeClaim claim, String runId) {
        if (claim == null || claim.getMetadata() == null || runId == null || !runId.matches("[0-9]+")) {
            return false;
        }
        Map<String, String> labels = claim.getMetadata().getLabels();
        return labels != null && "pipeline-build".equals(labels.get("app"))
                && runId.equals(labels.get("pipeline-run-id"))
                && ("pipeline-run-" + runId).equals(claim.getMetadata().getName());
    }

    /**
     * 删除 Pod。
     */
    private void deletePod(KubernetesClient client, String namespace, String podName) {
        client.pods()
                .inNamespace(namespace)
                .withName(podName)
                .delete();
    }

    /**
     * 判断是否是 K8s 执行器组。
     */
    private boolean isK8sExecutorGroup(String group) {
        return group != null && group.startsWith(EXECUTOR_GROUP_PREFIX);
    }

    /**
     * 解析 K8s 环境配置。
     */
    private KubernetesEnvironmentConfig parseK8sConfig(EnvironmentDO environment) {
        try {
            return cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                    environment.getInfraConfig(),
                    KubernetesEnvironmentConfig.class
            );
        } catch (Exception ex) {
            throw exception(ENVIRONMENT_KUBERNETES_CONFIG_INVALID, ex.getMessage());
        }
    }

    /**
     * 准备工作空间卷。
     */
    private void prepareWorkspaceVolume(KubernetesClient client, String namespace, PipelineRunDO run, PipelineWorkspace workspace,
                                       List<Volume> volumes, List<VolumeMount> volumeMounts) {
        DevopsConfig.WorkspaceStorageConfig storageConfig = devopsConfig.getBuild().getCluster().getWorkspaceStorage();
        String storageType = storageConfig.getType();

        Volume volume = new VolumeBuilder()
                .withName("workspace")
                .build();

        switch (storageType) {
            case "SHARED_PVC" -> {
                // 共享 PVC：用 subPath 直接挂载本次 run 的子目录到 /workspace。
                // 不用「挂到 /workspace-root + 容器内软链」的方式：软链会让 `pwd -P` 解析到 /workspace-root/...，
                // 从而绕过各步骤的工作目录校验（GitCheckoutScript 等）。
                String pvcName = storageConfig.getSharedPvcName();
                String runSubPath = "runs/" + run.getId();
                volume.setPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                        .withClaimName(pvcName)
                        .build());
                volumeMounts.add(new VolumeMountBuilder()
                        .withName("workspace")
                        .withMountPath(CONTAINER_WORKSPACE)
                        .withSubPath(runSubPath)
                        .build());
                workspace.getMetadata().put("workspaceSubPath", runSubPath);
                log.debug("[prepareWorkspaceVolume][runId({}) 使用共享 PVC: {}, 容器内子路径: {}]",
                        run.getId(), pvcName, runSubPath);
            }
            case "DYNAMIC_PVC" -> {
                String pvcName = "pipeline-run-" + run.getId();
                createDynamicPvcIfMissing(client, namespace, pvcName, run, storageConfig);
                volume.setPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                        .withClaimName(pvcName)
                        .build());
                volumeMounts.add(new VolumeMountBuilder()
                        .withName("workspace")
                        .withMountPath(CONTAINER_WORKSPACE)
                        .build());
                log.debug("[prepareWorkspaceVolume][runId({}) 使用动态 PVC: {}]", run.getId(), pvcName);
            }
            case "EMPTY_DIR" -> {
                // EmptyDir（临时存储，Pod 删除后数据丢失）
                volume.setEmptyDir(new EmptyDirVolumeSourceBuilder()
                        .withSizeLimit(new Quantity(storageConfig.getStorageSize()))
                        .build());
                volumeMounts.add(new VolumeMountBuilder()
                        .withName("workspace")
                        .withMountPath(CONTAINER_WORKSPACE)
                        .build());
                log.debug("[prepareWorkspaceVolume][runId({}) 使用 EmptyDir, size: {}]",
                        run.getId(), storageConfig.getStorageSize());
            }
            default -> throw new IllegalStateException("Unsupported storage type: " + storageType);
        }

        volumes.add(volume);
    }

    /**
     * 准备缓存卷。
     */
    private void prepareCacheVolumes(PipelineRunDO run, PipelineWorkspace workspace, List<Volume> volumes, List<VolumeMount> volumeMounts) {
        if (workspace.getCacheMounts() == null || workspace.getCacheMounts().isEmpty()) {
            return;
        }

        DevopsConfig.WorkspaceStorageConfig config = devopsConfig.getBuild().getCluster().getWorkspaceStorage();
        String pvc = StrUtil.isBlank(config.getCachePvcName()) && "SHARED_PVC".equals(config.getType())
                ? config.getSharedPvcName() : config.getCachePvcName();
        if (StrUtil.isBlank(pvc)) {
            log.warn("[prepareCacheVolumes] cache PVC is not configured; cache mounts are skipped");
            return;
        }

        // 挂载整个 cache PVC 到 /cache-root，不使用 subPath
        // 注意：kubelet 以 PVC 的 UniqueVolumeName 作为卷标识，同一个 Pod 中不能出现两个指向同一 PVC 的 Volume，
        // 否则卷协调会失败（unmounted volumes / context deadline exceeded）。SHARED_PVC 下 cache 与 workspace
        // 默认复用同一个 PVC，这里复用已有 Volume 名称，只追加挂载点。
        String cacheVolumeName = volumes.stream()
                .filter(volume -> volume.getPersistentVolumeClaim() != null
                        && pvc.equals(volume.getPersistentVolumeClaim().getClaimName()))
                .map(Volume::getName)
                .findFirst()
                .orElse(null);
        if (cacheVolumeName == null) {
            cacheVolumeName = "cache";
            volumes.add(new VolumeBuilder().withName(cacheVolumeName).withPersistentVolumeClaim(
                    new PersistentVolumeClaimVolumeSourceBuilder().withClaimName(pvc).build()).build());
        } else {
            log.debug("[prepareCacheVolumes][runId({}) 缓存与工作空间复用 PVC({})，复用 Volume: {}]",
                    run.getId(), pvc, cacheVolumeName);
        }
        volumeMounts.add(new VolumeMountBuilder().withName(cacheVolumeName).withMountPath("/cache-root").build());

        // 记录缓存路径映射到 workspace metadata，供容器启动脚本使用
        String scope = hashPath(run.getTenantId() + ":" + workspace.getCacheKey());
        List<Map<String, String>> cacheMappings = new ArrayList<>();
        for (PipelineCacheMount cache : workspace.getCacheMounts()) {
            if (StrUtil.isBlank(cache.getContainerPath()) || !cache.getContainerPath().startsWith("/")) {
                throw new IllegalArgumentException("Cache mount path must be absolute");
            }
            String pvcSubPath = "caches/" + scope + "/" + hashPath(cache.getCanonicalPath() == null
                    ? cache.getContainerPath() : cache.getCanonicalPath());
            Map<String, String> mapping = new HashMap<>();
            mapping.put("target", cache.getContainerPath());
            mapping.put("source", "/cache-root/" + pvcSubPath);
            cacheMappings.add(mapping);
        }
        workspace.getMetadata().put("cacheMappings", cacheMappings);
        log.debug("[prepareCacheVolumes][runId({}) 缓存挂载配置: {}]", run.getId(), cacheMappings.size());
    }

    private void createDynamicPvcIfMissing(KubernetesClient client, String namespace, String name, PipelineRunDO run,
                                           DevopsConfig.WorkspaceStorageConfig config) {
        PersistentVolumeClaim existing = client.persistentVolumeClaims().inNamespace(namespace).withName(name).get();
        if (existing != null) {
            Map<String, String> labels = existing.getMetadata().getLabels();
            if (labels == null || !String.valueOf(run.getId()).equals(labels.get("pipeline-run-id"))) {
                throw new IllegalStateException("Dynamic workspace PVC belongs to another run");
            }
            return;
        }
        PersistentVolumeClaim claim = new PersistentVolumeClaimBuilder().withNewMetadata().withName(name)
                .withNamespace(namespace).addToLabels("app", "pipeline-build")
                .addToLabels("pipeline-run-id", String.valueOf(run.getId())).endMetadata().withNewSpec()
                .withAccessModes(config.getAccessMode()).withStorageClassName(config.getStorageClassName())
                .withNewResources().addToRequests("storage", new Quantity(config.getStorageSize())).endResources()
                .endSpec().build();
        try {
            client.persistentVolumeClaims().inNamespace(namespace).resource(claim).create();
        } catch (KubernetesClientException ex) {
            if (ex.getCode() != 409) throw ex;
        }
    }

    private String hashPath(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    /**
     * 构建 Pod 对象。
     */
    private Pod buildPod(String podName, String namespace, String image,
                        PipelineRunDO run, PipelineSpec.ExecutableJob job,
                        List<Volume> volumes, List<VolumeMount> volumeMounts,
                        PipelineWorkspace workspace) {
        DevopsConfig.ResourceQuotaConfig quota = devopsConfig.getBuild().getCluster().getResourceQuota();

        // 准备环境变量（包括git凭证）
        List<EnvVar> envVars = new ArrayList<>();
        envVars.add(new EnvVarBuilder()
                .withName("PIPELINE_RUN_ID")
                .withValue(run.getId().toString())
                .build());
        envVars.add(new EnvVarBuilder()
                .withName("PIPELINE_JOB_ID")
                .withValue(job.getJobId())
                .build());

        // 注入git凭证（如果K8s中存在git-credentials Secret）
        injectGitCredentialsFromSecret(envVars, namespace);

        // 容器启动脚本：共享 PVC 子目录与缓存目录的软链，启动后常驻等待 exec
        String keeper = buildContainerBootstrap(workspace);
        ObjectMeta metadata = new ObjectMetaBuilder()
                    .withName(podName)
                    .withNamespace(namespace)
                    .addToLabels("app", "pipeline-build")
                    .addToLabels("pipeline-run-id", run.getId().toString())
                    .addToLabels("pipeline-job-id", safeLabel(job.getJobId()))
                    .addToAnnotations("created-at", String.valueOf(System.currentTimeMillis()))
                .build();
        List<Container> containers = new ArrayList<>();
        containers.add(new ContainerBuilder()
                        .withName("builder")
                        .withImage(image)
                        .withCommand("sh", "-c", keeper)
                        .withWorkingDir(CONTAINER_WORKSPACE)
                        .withVolumeMounts(volumeMounts)
                        .withEnv(envVars)  // 注入环境变量
                        .withNewResources()
                            .addToRequests("cpu", new Quantity(quota.getCpuRequest()))
                            .addToRequests("memory", new Quantity(quota.getMemoryRequest()))
                            .addToLimits("cpu", new Quantity(quota.getCpuLimit()))
                            .addToLimits("memory", new Quantity(quota.getMemoryLimit()))
                        .endResources()
                    .build());
        if (hasKanikoStep(job)) {
            DevopsConfig.ResourceQuotaConfig kanikoQuota = devopsConfig.getBuild().getCluster().getResourceQuota();
            containers.add(new ContainerBuilder()
                    .withName("kaniko")
                    .withImage(devopsConfig.getBuild().getCluster().getKanikoImage())
                    .withCommand("/busybox/sh", "-c", keeper)
                    .withWorkingDir(CONTAINER_WORKSPACE)
                    .withVolumeMounts(volumeMounts)
                    .withEnv(envVars)
                    .addToEnv(new EnvVarBuilder().withName("PATH")
                            .withValue("/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/busybox").build())
                    .withNewResources()
                        .addToRequests("cpu", new Quantity(kanikoQuota.getCpuRequest()))
                        .addToRequests("memory", new Quantity(kanikoQuota.getMemoryRequest()))
                        .addToLimits("cpu", new Quantity(kanikoQuota.getCpuLimit()))
                        .addToLimits("memory", new Quantity(kanikoQuota.getMemoryLimit()))
                    .endResources()
                    .build());
        }
        PodSpec spec = new PodSpecBuilder()
                .withContainers(containers)
                .withVolumes(volumes)
                .withRestartPolicy("Never")
                .withNewSecurityContext().withFsGroup(devopsConfig.getBuild().getCluster().getFsGroup()).endSecurityContext()
                .build();
        return new PodBuilder().withMetadata(metadata).withSpec(spec).build();
    }

    /**
     * 构建容器启动脚本。
     *
     * <p>共享 PVC 场景下工作目录由卷的 subPath 直接挂到 /workspace；缓存目录需要软链到缓存 PVC 的隔离子目录，
     * 使命令与步骤实现无需感知挂载细节。
     */
    private String buildContainerBootstrap(PipelineWorkspace workspace) {
        StringBuilder script = new StringBuilder("set -e\n");
        Map<String, Object> metadata = workspace == null || workspace.getMetadata() == null
                ? Collections.emptyMap() : workspace.getMetadata();
        // 依赖缓存：把容器内缓存路径软链到缓存 PVC 的租户/流水线隔离子目录
        Object cacheMappings = metadata.get("cacheMappings");
        if (cacheMappings instanceof List<?> mappings) {
            for (Object item : mappings) {
                if (!(item instanceof Map<?, ?> mapping)) {
                    continue;
                }
                String target = mapping.get("target") == null ? null : String.valueOf(mapping.get("target"));
                String source = mapping.get("source") == null ? null : String.valueOf(mapping.get("source"));
                if (StrUtil.isBlank(target) || StrUtil.isBlank(source)) {
                    continue;
                }
                script.append("mkdir -p ").append(quote(source)).append('\n');
                script.append("if [ -d ").append(quote(target)).append(" ] && [ ! -L ").append(quote(target))
                        .append(" ]; then rm -rf ").append(quote(target)).append("; fi\n");
                script.append("mkdir -p \"$(dirname ").append(quote(target)).append(")\"\n");
                script.append("ln -sfn ").append(quote(source)).append(' ').append(quote(target)).append('\n');
            }
        }
        script.append("trap 'exit 0' TERM INT; while :; do sleep 86400 & wait $!; done");
        return script.toString();
    }

    private boolean hasKanikoStep(PipelineSpec.ExecutableJob job) {
        return job.getSteps() != null && job.getSteps().stream().anyMatch(step ->
                PipelineNodeRegistryServiceImpl.TYPE_KANIKO_BUILD.equals(step.getStep())
                        || "KANIKO_DOCKER_BUILD".equals(step.getStep()));
    }

    /**
     * 从K8s Secret注入git凭证环境变量。
     *
     * <p>期望在构建集群的namespace中存在名为 git-credentials 的Secret，包含：
     * <ul>
     *     <li>username: Git用户名</li>
     *     <li>password: Git密码或Token</li>
     * </ul>
     */
    private void injectGitCredentialsFromSecret(List<EnvVar> envVars, String namespace) {
        String secretName = "git-credentials";

        // GIT_USERNAME
        envVars.add(new EnvVarBuilder()
                .withName("GIT_USERNAME")
                .withNewValueFrom()
                    .withNewSecretKeyRef()
                        .withName(secretName)
                        .withKey("username")
                        .withOptional(true)  // 可选，不存在时不报错
                    .endSecretKeyRef()
                .endValueFrom()
                .build());

        // GIT_PASSWORD
        envVars.add(new EnvVarBuilder()
                .withName("GIT_PASSWORD")
                .withNewValueFrom()
                    .withNewSecretKeyRef()
                        .withName(secretName)
                        .withKey("password")
                        .withOptional(true)
                    .endSecretKeyRef()
                .endValueFrom()
                .build());

        // GIT_USER_NAME (用于git commit)
        envVars.add(new EnvVarBuilder()
                .withName("GIT_USER_NAME")
                .withNewValueFrom()
                    .withNewSecretKeyRef()
                        .withName(secretName)
                        .withKey("user-name")
                        .withOptional(true)
                    .endSecretKeyRef()
                .endValueFrom()
                .build());

        // GIT_USER_EMAIL (用于git commit)
        envVars.add(new EnvVarBuilder()
                .withName("GIT_USER_EMAIL")
                .withNewValueFrom()
                    .withNewSecretKeyRef()
                        .withName(secretName)
                        .withKey("user-email")
                        .withOptional(true)
                    .endSecretKeyRef()
                .endValueFrom()
                .build());

        log.debug("[injectGitCredentialsFromSecret][namespace({}) 注入git凭证环境变量from Secret: {}]",
                namespace, secretName);
    }

    /**
     * 等待 Pod Ready。
     */
    private void waitPodReady(KubernetesClient client, String namespace, String podName) {
        log.debug("[waitPodReady][namespace({}) podName({}) 等待 Pod Ready]", namespace, podName);

        try {
            PodResource podResource = client.pods().inNamespace(namespace).withName(podName);
            Pod pod = podResource.waitUntilReady(POD_READY_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (pod == null || pod.getStatus() == null || !"Running".equals(pod.getStatus().getPhase())) {
                throw new IllegalStateException("Pod 未能成功启动");
            }

            log.info("[waitPodReady][namespace({}) podName({}) Pod 已 Ready]", namespace, podName);
        } catch (Exception ex) {
            throw new IllegalStateException("等待 Pod Ready 超时或失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 生成 Pod 名称。
     */
    private String generatePodName(PipelineRunDO run, PipelineSpec.ExecutableJob job) {
        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        // K8s 名称规范：小写字母、数字、短横线，最长 63 字符
        String value = String.format("pipeline-%s-%s-%s", run.getId(), job.getJobId(), shortId)
                .toLowerCase()
                .replaceAll("[^a-z0-9-]", "-");
        if (value.length() > 63) value = value.substring(0, 54) + "-" + shortId;
        return value;
    }

    private void validateCluster(String image) {
        DevopsConfig.BuildClusterConfig cluster = devopsConfig.getBuild().getCluster();
        if (StrUtil.isBlank(image) || StrUtil.isBlank(cluster.getNamespace())
                || !cluster.getNamespace().matches("[a-z0-9](?:[-a-z0-9]{0,61}[a-z0-9])?")) {
            throw new IllegalArgumentException("Invalid Kubernetes namespace or builder image");
        }
        String type = cluster.getWorkspaceStorage().getType();
        if (!Set.of("SHARED_PVC", "DYNAMIC_PVC", "EMPTY_DIR").contains(type)) {
            throw new IllegalArgumentException("Unsupported workspace storage type: " + type);
        }
    }

    private String safeLabel(String value) {
        String normalized = String.valueOf(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9.-]", "-").replaceAll("^[^a-z0-9]+|[^a-z0-9]+$", "");
        if (normalized.isEmpty()) normalized = "job";
        return normalized.length() <= 63 ? normalized : normalized.substring(0, 54) + "-" +
                UUID.nameUUIDFromBytes(normalized.getBytes()).toString().substring(0, 8);
    }

    /**
     * 构建运行时元数据。
     */
    private Map<String, Object> buildRuntimeMetadata(String namespace, Long environmentId,
                                                     KubernetesEnvironmentConfig envConfig) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("namespace", namespace);
        metadata.put("environmentId", environmentId);
        metadata.put("workspaceStorageType", devopsConfig.getBuild().getCluster().getWorkspaceStorage().getType());
        metadata.put("kubeconfig", envConfig.getKubeconfig());
        return metadata;
    }

    /**
     * 从运行时获取环境配置。
     */
    private KubernetesEnvironmentConfig getEnvConfigFromRuntime(PipelineJobRuntime runtime) {
        String kubeconfig = (String) runtime.getMetadata().get("kubeconfig");
        KubernetesEnvironmentConfig config = new KubernetesEnvironmentConfig();
        config.setKubeconfig(kubeconfig);
        return config;
    }

}
