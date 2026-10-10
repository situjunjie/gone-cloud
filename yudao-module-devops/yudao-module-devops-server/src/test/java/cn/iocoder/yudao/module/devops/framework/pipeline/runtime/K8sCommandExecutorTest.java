package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesClientFactory;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.RequestConfig;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class K8sCommandExecutorTest {

    @Test
    void usesProcessExitStatusAndClosesWatchAndClient() {
        Harness harness = new Harness(CompletableFuture.completedFuture(23));

        ExecResult result = harness.executor.exec(harness.context(10), "exit 23", null);

        assertEquals(23, result.getExitCode());
        verify(harness.watch).exitCode();
        verify(harness.watch).close();
        verify(harness.client).close();
        verify(harness.pod, never()).withGracePeriod(anyLong());
    }

    @Test
    void acceptsZeroAndRejectsMissingProcessStatus() {
        Harness success = new Harness(CompletableFuture.completedFuture(0));
        Harness missing = new Harness(CompletableFuture.completedFuture(null));
        assertTrue(success.executor.exec(success.context(10), "true", null).isSuccess());
        assertFalse(missing.executor.exec(missing.context(10), "true", null).isSuccess());
    }

    @Test
    void cancellationDeletesPodClosesWatchAndCannotReturnSuccess() throws Exception {
        CompletableFuture<Integer> status = new CompletableFuture<>();
        Harness harness = new Harness(status);
        CountDownLatch attached = new CountDownLatch(1);
        when(harness.watch.exitCode()).thenAnswer(invocation -> {
            attached.countDown();
            return status;
        });
        // A connection closing concurrently with cancellation must not turn cancellation into success.
        doAnswer(invocation -> { status.complete(0); return null; }).when(harness.watch).close();
        var worker = Executors.newSingleThreadExecutor();
        try {
            var execution = worker.submit(() -> harness.executor.exec(harness.context(10), "sleep 100", null));
            assertTrue(attached.await(3, TimeUnit.SECONDS));
            harness.executor.cancel("run:job");

            assertFalse(execution.get(3, TimeUnit.SECONDS).isSuccess());
            verify(harness.pod).withGracePeriod(0);
            verify(harness.pod).delete();
            verify(harness.watch).close();
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void timeoutStopsRemoteProcessAndReleasesConnection() {
        Harness harness = new Harness(new CompletableFuture<>());
        ExecResult result = harness.executor.exec(harness.context(1), "sleep 100", null);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("timeout"));
        verify(harness.pod).delete();
        verify(harness.watch).close();
        verify(harness.client).close();
    }

    @Test
    void connectionFailureDeletesPodBeforeClosingClient() {
        Harness harness = new Harness(CompletableFuture.failedFuture(new IllegalStateException("connection lost")));

        assertFalse(harness.executor.exec(harness.context(10), "sleep 100", null).isSuccess());

        var order = inOrder(harness.pod, harness.client);
        order.verify(harness.pod).delete();
        order.verify(harness.client).close();
    }

    @Test
    void streamsSplitUtf8LinesOnceBeforeCompletion() throws Exception {
        Harness harness = new Harness(CompletableFuture.completedFuture(0));
        List<String> lines = new ArrayList<>();
        when(harness.container.exec(any(String[].class))).thenAnswer(invocation -> {
            var output = org.mockito.ArgumentCaptor.forClass(OutputStream.class);
            verify(harness.container).writingOutput(output.capture());
            byte[] bytes = "\u4e2d\u6587\ntrailing".getBytes(StandardCharsets.UTF_8);
            output.getValue().write(bytes, 0, 2);
            assertTrue(lines.isEmpty());
            output.getValue().write(bytes, 2, bytes.length - 2);
            assertEquals(List.of("stdout:\u4e2d\u6587"), lines);
            return harness.watch;
        });
        harness.executor.exec(harness.context(10), "printf log", (stream, line) -> lines.add(stream + ":" + line));
        assertEquals(List.of("stdout:\u4e2d\u6587", "stdout:trailing"), lines);
    }

    private static class Harness {
        final K8sCommandExecutor executor = new K8sCommandExecutor();
        final KubernetesClient client = mock(KubernetesClient.class);
        final PodResource pod = mock(PodResource.class, RETURNS_SELF);
        final ContainerResource container = mock(ContainerResource.class, RETURNS_SELF);
        final ExecWatch watch = mock(ExecWatch.class);

        @SuppressWarnings("unchecked")
        Harness(CompletableFuture<Integer> status) {
            KubernetesClientFactory factory = mock(KubernetesClientFactory.class);
            ReflectionTestUtils.setField(executor, "kubernetesClientFactory", factory);
            when(factory.create("config")).thenReturn(client);
            when(client.getConfiguration()).thenReturn(Config.empty());
            when(client.newClient(any(RequestConfig.class))).thenReturn(client);
            when(client.adapt(KubernetesClient.class)).thenReturn(client);
            MixedOperation<Pod, PodList, PodResource> pods = mock(MixedOperation.class, RETURNS_SELF);
            when(client.pods()).thenReturn(pods);
            doReturn(pods).when(pods).inNamespace("build");
            when(pods.withName("build-pod")).thenReturn(pod);
            when(pod.inContainer("builder")).thenReturn(container);
            doReturn(pod).when(pod).withGracePeriod(0);
            when(container.exec(any(String[].class))).thenReturn(watch);
            when(watch.exitCode()).thenReturn(status);
        }

        PipelineCommandContext context(long timeout) {
            return PipelineCommandContext.builder().runId("run:job").timeoutSeconds(timeout)
                    .runtime(PipelineJobRuntime.builder().runtimeType("KUBERNETES").runtimeId("build-pod")
                            .metadata(Map.of("namespace", "build", "kubeconfig", "config")).build()).build();
        }
    }

    @Test
    void buildScriptQuotesEnvironmentAndWorkingDirectory() {
        PipelineCommandContext context = PipelineCommandContext.builder()
                .env(Map.of("TOKEN", "a'b; echo injected"))
                .workingDirectory("/workspace/job dir")
                .build();

        String script = K8sCommandExecutor.buildScript(context, "printf '%s\\n' \"$TOKEN\"");

        assertEquals("set -e\nexport TOKEN='a'\"'\"'b; echo injected'\ncd '/workspace/job dir'\nprintf '%s\\n' \"$TOKEN\"\n", script);
    }

    @Test
    void rejectsInvalidEnvironmentName() {
        PipelineCommandContext context = PipelineCommandContext.builder()
                .env(Map.of("TOKEN;rm", "x"))
                .build();

        assertThrows(IllegalArgumentException.class, () -> K8sCommandExecutor.buildScript(context, "true"));
    }
}
