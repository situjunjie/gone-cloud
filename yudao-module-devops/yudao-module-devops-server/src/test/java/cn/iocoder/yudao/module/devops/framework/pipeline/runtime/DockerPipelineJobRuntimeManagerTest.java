package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.docker.DockerClientFactory;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DockerPipelineJobRuntimeManager} 的单元测试。
 */
class DockerPipelineJobRuntimeManagerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private DockerPipelineJobRuntimeManager runtimeManager;

    @Mock
    private DockerClientFactory dockerClientFactory;
    @Mock
    private DockerClient dockerClient;
    @Mock
    private CreateContainerCmd createContainerCmd;
    @Mock
    private StartContainerCmd startContainerCmd;

    @Test
    void testCreateRuntime_mountsRunWorkspaceAndCacheDirectories() {
        when(dockerClientFactory.getDefaultClient()).thenReturn(dockerClient);
        when(dockerClient.createContainerCmd(eq("maven:3.9"))).thenReturn(createContainerCmd);
        when(createContainerCmd.withName(any())).thenReturn(createContainerCmd);
        when(createContainerCmd.withWorkingDir(eq("/workspace"))).thenReturn(createContainerCmd);
        when(createContainerCmd.withHostConfig(any())).thenReturn(createContainerCmd);
        when(createContainerCmd.withCmd(eq("sh"), eq("-c"), eq("while true; do sleep 30; done")))
                .thenReturn(createContainerCmd);
        CreateContainerResponse response = new CreateContainerResponse();
        response.setId("container-1");
        when(createContainerCmd.exec()).thenReturn(response);
        when(dockerClient.startContainerCmd(eq("container-1"))).thenReturn(startContainerCmd);

        PipelineJobRuntime runtime = runtimeManager.createRuntime(run(), job(), workspace());

        assertEquals("container-1", runtime.getRuntimeId());
        assertEquals(Path.of("/tmp/run-1"), runtime.getWorkspace());
        assertEquals("definition-30", runtime.getCacheKey());
        ArgumentCaptor<HostConfig> hostConfigCaptor = ArgumentCaptor.forClass(HostConfig.class);
        verify(createContainerCmd).withHostConfig(hostConfigCaptor.capture());
        Set<String> binds = Arrays.stream(hostConfigCaptor.getValue().getBinds())
                .map(this::formatBind)
                .collect(Collectors.toSet());
        assertTrue(binds.contains("/tmp/run-1:/workspace"));
        assertTrue(binds.contains("/tmp/cache/m2:/root/.m2"));
        assertTrue(binds.contains("/tmp/cache/npm:/root/.npm"));
        verify(dockerClient).startContainerCmd(eq("container-1"));
    }

    private String formatBind(Bind bind) {
        return bind.getPath() + ":" + bind.getVolume().getPath();
    }

    private PipelineRunDO run() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        return run;
    }

    private PipelineSpec.ExecutableJob job() {
        PipelineSpec.ExecutableJob job = new PipelineSpec.ExecutableJob();
        job.setJobId("build");
        PipelineSpec.RunsOn runsOn = new PipelineSpec.RunsOn();
        runsOn.setGroup("local-docker/default");
        runsOn.setContainer("maven:3.9");
        job.setRunsOn(runsOn);
        return job;
    }

    private PipelineWorkspace workspace() {
        return PipelineWorkspace.builder()
                .runWorkspace(Path.of("/tmp/run-1"))
                .cacheWorkspace(Path.of("/tmp/cache"))
                .cacheKey("definition-30")
                .cacheMounts(List.of(
                        PipelineCacheMount.builder()
                                .id("maven")
                                .containerPath("/root/.m2")
                                .hostPath(Path.of("/tmp/cache/m2"))
                                .build(),
                        PipelineCacheMount.builder()
                                .id("npm")
                                .containerPath("/root/.npm")
                                .hostPath(Path.of("/tmp/cache/npm"))
                                .build()))
                .build();
    }

}
