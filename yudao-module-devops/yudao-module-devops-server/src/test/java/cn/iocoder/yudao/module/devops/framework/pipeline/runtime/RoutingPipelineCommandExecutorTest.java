package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineLogStorageService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineRunLogLineService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.CommandStepHandler;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepLogHelper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoutingPipelineCommandExecutorTest {

    private final DockerPipelineCommandExecutor docker = mock(DockerPipelineCommandExecutor.class);
    private final K8sCommandExecutor kubernetes = mock(K8sCommandExecutor.class);
    private final RoutingPipelineCommandExecutor router = new RoutingPipelineCommandExecutor(docker, kubernetes);

    @Test
    void dockerCommandsOnlyUseDocker() {
        PipelineCommandContext context = context("DOCKER");
        when(docker.exec(context, "true", null)).thenReturn(ExecResult.success());
        assertTrue(router.exec(context, "true", null).isSuccess());
        verify(docker).exec(context, "true", null);
        verifyNoInteractions(kubernetes);
    }

    @Test
    void kubernetesCommandsOnlyUseKubernetes() {
        PipelineCommandContext context = context("KUBERNETES");
        when(kubernetes.exec(context, "false", null)).thenReturn(ExecResult.failure(5, "failed"));
        assertEquals(5, router.exec(context, "false", null).getExitCode());
        verify(kubernetes).exec(context, "false", null);
        verifyNoInteractions(docker);
    }

    @Test
    void cancelsBothBackendsEvenWhenDockerCancellationThrows() {
        doThrow(new IllegalStateException("docker unavailable")).when(docker).cancel("run:job");
        assertThrows(IllegalStateException.class, () -> router.cancel("run:job"));
        verify(docker).cancel("run:job");
        verify(kubernetes).cancel("run:job");
    }

    @Test
    void springInjectsPrimaryRouterIntoActualCommandHandler() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("dockerPipelineCommandExecutor", docker);
            context.getBeanFactory().registerSingleton("k8sCommandExecutor", kubernetes);
            context.getBeanFactory().registerSingleton("pipelineStepLogHelper", mock(PipelineStepLogHelper.class));
            context.getBeanFactory().registerSingleton("pipelineLogStorageService", mock(PipelineLogStorageService.class));
            context.getBeanFactory().registerSingleton("pipelineRunLogLineService", mock(PipelineRunLogLineService.class));
            context.register(RoutingPipelineCommandExecutor.class, CommandStepHandler.class);
            context.refresh();

            Object injected = ReflectionTestUtils.getField(context.getBean(CommandStepHandler.class), "pipelineCommandExecutor");
            assertSame(context.getBean(RoutingPipelineCommandExecutor.class), injected);
            assertSame(injected, context.getBean(PipelineCommandExecutor.class));
        }
    }

    private PipelineCommandContext context(String type) {
        return PipelineCommandContext.builder().runtime(PipelineJobRuntime.builder().runtimeType(type).build()).build();
    }
}
