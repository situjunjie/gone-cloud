package cn.iocoder.yudao.module.devops.framework.git;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.build.LogSink;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandContext;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandExecutor;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntimeManagerFactory;
import cn.iocoder.yudao.module.devops.service.environment.EnvironmentService;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RuntimeGitWorkspaceSupportTest extends BaseMockitoUnitTest {
    @InjectMocks
    private RuntimeGitWorkspaceSupport support;
    @Mock
    private PipelineJobRuntimeManagerFactory runtimeManagerFactory;
    @Mock
    private PipelineCommandExecutor executor;
    @Mock
    private EnvironmentService environmentService;
    @Mock
    private RepositoryProviderService repositoryProviderService;
    @TempDir
    Path workspace;
    private Path routedWorkspace;

    @BeforeEach
    void setupRuntime() throws Exception {
        workspace = workspace.toRealPath();
        EnvironmentDO environment = new EnvironmentDO();
        environment.setInfraConfig("{\"kubeconfig\":\"test-config\"}");
        when(environmentService.getEnvironment(11L)).thenReturn(environment);
        when(runtimeManagerFactory.selectCommandExecutor(any())).thenReturn(executor);
        when(executor.exec(any(), any(), any())).thenAnswer(invocation -> {
            PipelineCommandContext context = invocation.getArgument(0);
            assertEquals("KUBERNETES", context.getRuntime().getRuntimeType());
            assertEquals("merge-pod", context.getRuntime().getRuntimeId());
            assertEquals("builds", context.getRuntime().getMetadata().get("namespace"));
            String script = invocation.<String>getArgument(1).replace("/workspace", workspace.toString());
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", script)
                    .directory(Path.of(context.getWorkingDirectory().replace("/workspace", workspace.toString())).toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.environment().putAll(context.getEnv());
            Process process = builder.start();
            LogSink sink = invocation.getArgument(2);
            try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
                reader.lines().forEach(line -> sink.accept("STDOUT", line));
            }
            int code = process.waitFor();
            return code == 0 ? ExecResult.success() : ExecResult.failure(code, "command failed");
        });
        GitWorkspaceRuntimeRef ref = new GitWorkspaceRuntimeRef();
        ref.setEnvironmentId(11L);
        ref.setNamespace("builds");
        ref.setPodName("merge-pod");
        routedWorkspace = support.register(ref);
    }

    @Test
    void credentialsAreResolvedPerCommandAndNeverEmbeddedInScript() {
        GitWorkspaceRuntimeRef ref = new GitWorkspaceRuntimeRef();
        ref.setEnvironmentId(11L);
        ref.setNamespace("builds");
        ref.setPodName("merge-pod");
        ref.setRepositoryProviderId(22L);
        RepositoryProviderDO provider = new RepositoryProviderDO();
        provider.setAccessToken("secret'$(touch nope)");
        when(repositoryProviderService.getRepositoryProvider(22L)).thenReturn(provider);
        support.register(ref);
        support.git(routedWorkspace, List.of("git", "--version"), true);
        var context = org.mockito.ArgumentCaptor.forClass(PipelineCommandContext.class);
        var script = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(executor).exec(context.capture(), script.capture(), any());
        assertEquals(provider.getAccessToken(), context.getValue().getEnv().get("GIT_PASSWORD"));
        assertFalse(script.getValue().contains(provider.getAccessToken()));
        assertFalse(Files.exists(workspace.resolve("nope")));
    }

    @Test
    void conflictFilesPreserveBytesAndRejectSymlinkWrites() throws Exception {
        byte[] content = "resolved\nwithout trailing newline".getBytes(StandardCharsets.UTF_8);
        support.write(routedWorkspace.resolve("src/conflict file.txt"), content);
        assertArrayEquals(content, support.read(routedWorkspace.resolve("src/conflict file.txt")));
        assertTrue(support.exists(routedWorkspace.resolve("src/conflict file.txt")));
        Path outside = Files.createTempFile("pipeline-conflict", ".txt");
        try {
            Files.createSymbolicLink(workspace.resolve("link"), outside);
            assertThrows(GitCommandException.class, () -> support.write(routedWorkspace.resolve("link"), content));
            assertEquals(0, Files.size(outside));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void mergeConflictCanResumeThroughReboundPodWorkspace() throws Exception {
        support.git(routedWorkspace, List.of("git", "init", "-b", "main"), true);
        support.git(routedWorkspace, List.of("git", "config", "user.email", "test@example.com"), true);
        support.git(routedWorkspace, List.of("git", "config", "user.name", "Test"), true);
        Files.writeString(workspace.resolve("file.txt"), "base");
        support.git(routedWorkspace, List.of("git", "add", "."), true);
        support.git(routedWorkspace, List.of("git", "commit", "-m", "base"), true);
        support.git(routedWorkspace, List.of("git", "checkout", "-b", "feature"), true);
        Files.writeString(workspace.resolve("file.txt"), "theirs");
        support.git(routedWorkspace, List.of("git", "commit", "-am", "feature"), true);
        support.git(routedWorkspace, List.of("git", "checkout", "main"), true);
        Files.writeString(workspace.resolve("file.txt"), "ours");
        support.git(routedWorkspace, List.of("git", "commit", "-am", "main"), true);
        GitWorkspaceServiceImpl service = new GitWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service, "runtimeGitWorkspaceSupport", support);
        GitCommandExecutor localGit = mock(GitCommandExecutor.class);
        ReflectionTestUtils.setField(service, "gitCommandExecutor", localGit);
        service.registerExternalWorkspace("merge", routedWorkspace);
        GitMergeResult merge = service.merge("merge", "feature", "merge feature");
        assertEquals(GitMergeResult.STATUS_CONFLICTING, merge.getStatus());
        GitConflictDescriptor conflict = merge.getConflicts().get(0);
        assertTrue(service.isConflictWorkspaceValid("merge", conflict));
        assertEquals("ours", service.readConflictContent("merge", conflict).getOursContent());
        GitFileResolution resolution = new GitFileResolution("file.txt", "resolved");
        service.continueMerge("merge", List.of(resolution), "resolved merge");
        assertEquals("resolved", Files.readString(workspace.resolve("file.txt")));
        verifyNoInteractions(localGit);
    }
}
