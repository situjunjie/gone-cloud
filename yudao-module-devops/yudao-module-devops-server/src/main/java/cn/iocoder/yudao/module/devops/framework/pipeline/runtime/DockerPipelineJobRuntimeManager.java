package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.docker.DockerClientFactory;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 基于本地 Docker daemon 的流水线任务运行时管理器。
 */
@Slf4j
@Component
public class DockerPipelineJobRuntimeManager implements PipelineJobRuntimeManager {

    private static final String DEFAULT_EXECUTOR_GROUP = "local-docker/default";
    private static final String CONTAINER_WORKSPACE = PipelineWorkspaceConstants.CONTAINER_WORKSPACE;
    private static final String CONTAINER_LOG_ROOT = "/pipeline-logs";

    @Resource
    private DockerClientFactory dockerClientFactory;
    @Value("${yudao.devops.pipeline.logs.root:${java.io.tmpdir}/gone-devops/pipeline-logs}")
    private String logRoot;

    @Override
    public PipelineJobRuntime createRuntime(PipelineRunDO run, PipelineSpec.ExecutableJob job, PipelineWorkspace workspace) {
        PipelineSpec.RunsOn runsOn = job.getRunsOn();
        String group = runsOn == null ? null : runsOn.getGroup();
        String image = runsOn == null ? null : runsOn.getContainer();
        if (!DEFAULT_EXECUTOR_GROUP.equals(group)) {
            throw new IllegalStateException("Unsupported executor group: " + group);
        }
        if (StrUtil.isBlank(image)) {
            throw new IllegalStateException("Executor image is required");
        }
        String containerName = "pipeline-" + run.getId() + "-" + job.getJobId() + "-" + shortId();
        DockerClient client = dockerClientFactory.getDefaultClient();
        ensureLogRootExists();
        List<Bind> binds = new ArrayList<>();
        binds.add(new Bind(workspace.getRunWorkspace().toString(), new Volume(CONTAINER_WORKSPACE)));
        binds.add(new Bind(logRootPath().toString(), new Volume(CONTAINER_LOG_ROOT)));
        if (workspace.getCacheMounts() != null) {
            for (PipelineCacheMount cacheMount : workspace.getCacheMounts()) {
                binds.add(new Bind(cacheMount.getHostPath().toString(), new Volume(cacheMount.getContainerPath())));
            }
        }
        CreateContainerResponse response = client.createContainerCmd(image)
                .withName(containerName)
                .withWorkingDir(CONTAINER_WORKSPACE)
                .withHostConfig(HostConfig.newHostConfig()
                        .withAutoRemove(false)
                        .withBinds(binds))
                .withCmd("sh", "-c", "while true; do sleep 30; done")
                .exec();
        client.startContainerCmd(response.getId()).exec();
        return PipelineJobRuntime.builder()
                .runtimeType("DOCKER")
                .runtimeId(response.getId())
                .runtimeName(containerName)
                .executorGroup(group)
                .executorImage(image)
                .workspace(workspace.getRunWorkspace())
                .cacheKey(workspace.getCacheKey())
                .cacheMounts(workspace.getCacheMounts())
                .build();
    }

    @Override
    public void destroyRuntime(PipelineJobRuntime runtime) {
        if (runtime == null || StrUtil.isBlank(runtime.getRuntimeId())) {
            return;
        }
        DockerClient client = dockerClientFactory.getDefaultClient();
        try {
            client.stopContainerCmd(runtime.getRuntimeId()).withTimeout(5).exec();
        } catch (Exception ex) {
            log.debug("[destroyRuntime][runtimeId({}) stop ignored: {}]", runtime.getRuntimeId(), ex.getMessage());
        }
        try {
            client.removeContainerCmd(runtime.getRuntimeId()).withForce(true).exec();
        } catch (Exception ex) {
            log.warn("[destroyRuntime][runtimeId({}) remove failed: {}]", runtime.getRuntimeId(), ex.getMessage());
        }
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private void ensureLogRootExists() {
        try {
            Files.createDirectories(logRootPath());
        } catch (Exception ex) {
            throw new IllegalStateException("Create pipeline log root failed: " + ex.getMessage(), ex);
        }
    }

    private Path logRootPath() {
        return Path.of(logRoot).toAbsolutePath().normalize();
    }

}
