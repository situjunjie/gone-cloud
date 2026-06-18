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
import org.springframework.stereotype.Component;

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
    private static final String CONTAINER_WORKSPACE = "/workspace";

    @Resource
    private DockerClientFactory dockerClientFactory;

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
        List<Bind> binds = new ArrayList<>();
        binds.add(new Bind(workspace.getRunWorkspace().toString(), new Volume(CONTAINER_WORKSPACE)));
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

}
