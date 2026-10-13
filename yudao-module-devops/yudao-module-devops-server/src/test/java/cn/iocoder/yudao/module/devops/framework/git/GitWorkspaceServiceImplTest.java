package cn.iocoder.yudao.module.devops.framework.git;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GitWorkspaceServiceImpl} 的单元测试。
 */
public class GitWorkspaceServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private GitWorkspaceServiceImpl gitWorkspaceService;

    @Mock
    private GitCommandExecutor gitCommandExecutor;

    @TempDir
    private Path tempDir;

    /** 与 {@code GitWorkspaceServiceImpl.FETCH_BLOB_NONE_FILTER} 一致；用字面量断言防止契约漂移。 */
    private static final String BLOB_NONE_FILTER = "--filter=blob:none";

    @Test
    public void testPrepareWorkspace_reuseRemoteDeployBranchAndMergeLatestBase() {
        // 准备参数
        ReflectionTestUtils.setField(gitWorkspaceService, "workspaceRoot", "/tmp/gone-devops/git-workspaces");
        when(gitCommandExecutor.execute(any(), any(), anyBoolean()))
                .thenReturn(new GitCommandResult(0, ""));
        when(gitCommandExecutor.execute(any(), eq(List.of("git", "rev-parse", "origin/master")), eq(true)))
                .thenReturn(new GitCommandResult(0, "base-head\n"));
        when(gitCommandExecutor.execute(any(), eq(List.of("git", "rev-parse", "HEAD")), eq(true)))
                .thenReturn(new GitCommandResult(0, "deploy-head\n"));

        // 调用
        GitWorkspacePrepareResult result = gitWorkspaceService.prepareWorkspace(1L,
                "https://gitlab/group/repo.git", "token", "master", "deploy/gone/test/20260607120000");

        // 断言
        assertEquals("deploy-head", result.getBaseCommitSha());
        assertEquals("base-head", result.getBaseRemoteCommitSha());
        assertEquals("deploy-head", result.getPreparedCommitSha());
        assertTrue(result.getDeployBranchExisted());
        verify(gitCommandExecutor).execute(any(), eq(List.of("git", "fetch", BLOB_NONE_FILTER, "origin", "master")), eq(true));
        verify(gitCommandExecutor).execute(any(), eq(List.of("git", "fetch", BLOB_NONE_FILTER, "origin",
                "deploy/gone/test/20260607120000")), eq(false));
        verify(gitCommandExecutor).execute(any(), eq(List.of("git", "checkout", "-B",
                "deploy/gone/test/20260607120000", "origin/deploy/gone/test/20260607120000")), eq(true));
        verify(gitCommandExecutor).execute(any(), eq(List.of("git", "merge", "--no-ff", "--no-commit",
                "base-head")), eq(false));
    }

    @Test
    public void testPrepareWorkspace_fallbackToBaseBranchWhenDeployBranchMissing() {
        // 准备参数
        ReflectionTestUtils.setField(gitWorkspaceService, "workspaceRoot", "/tmp/gone-devops/git-workspaces");
        when(gitCommandExecutor.execute(any(), any(), anyBoolean()))
                .thenReturn(new GitCommandResult(0, ""));
        when(gitCommandExecutor.execute(any(), eq(List.of("git", "rev-parse", "origin/master")), eq(true)))
                .thenReturn(new GitCommandResult(0, "base-head\n"));
        when(gitCommandExecutor.execute(any(), eq(List.of("git", "fetch", BLOB_NONE_FILTER,
                "origin", "deploy/gone/test/20260607120000")),
                eq(false))).thenReturn(new GitCommandResult(128, "not found"));
        when(gitCommandExecutor.execute(any(), eq(List.of("git", "rev-parse", "HEAD")), eq(true)))
                .thenReturn(new GitCommandResult(0, "base-head\n"));

        // 调用
        GitWorkspacePrepareResult result = gitWorkspaceService.prepareWorkspace(1L,
                "https://gitlab/group/repo.git", "token", "master", "deploy/gone/test/20260607120000");

        // 断言
        assertEquals("base-head", result.getBaseCommitSha());
        assertEquals("base-head", result.getBaseRemoteCommitSha());
        assertFalse(result.getDeployBranchExisted());
        verify(gitCommandExecutor).execute(any(), eq(List.of("git", "fetch", BLOB_NONE_FILTER, "origin", "master")), eq(true));
        verify(gitCommandExecutor).execute(any(), eq(List.of("git", "fetch", BLOB_NONE_FILTER, "origin",
                "deploy/gone/test/20260607120000")), eq(false));
        verify(gitCommandExecutor).execute(any(), eq(List.of("git", "checkout", "-B",
                "deploy/gone/test/20260607120000", "origin/master")), eq(true));
        verify(gitCommandExecutor, never()).execute(any(), eq(List.of("git", "merge", "--no-ff", "--no-commit",
                "base-head")), eq(false));
    }

    @Test
    public void testPrepareWorkspace_sameBaseAndDeployBranchSkipsSelfMerge() {
        // 准备参数
        ReflectionTestUtils.setField(gitWorkspaceService, "workspaceRoot", "/tmp/gone-devops/git-workspaces");
        when(gitCommandExecutor.execute(any(), any(), anyBoolean()))
                .thenReturn(new GitCommandResult(0, ""));
        when(gitCommandExecutor.execute(any(), eq(List.of("git", "rev-parse", "origin/master")), eq(true)))
                .thenReturn(new GitCommandResult(0, "base-head\n"));
        when(gitCommandExecutor.execute(any(), eq(List.of("git", "rev-parse", "HEAD")), eq(true)))
                .thenReturn(new GitCommandResult(0, "base-head\n"));

        // 调用
        GitWorkspacePrepareResult result = gitWorkspaceService.prepareWorkspace(1L,
                "https://gitlab/group/repo.git", "token", "master", "master");

        // 断言
        assertEquals("base-head", result.getBaseCommitSha());
        assertEquals("base-head", result.getBaseRemoteCommitSha());
        assertTrue(result.getDeployBranchExisted());
        verify(gitCommandExecutor).execute(any(), eq(List.of("git", "fetch", BLOB_NONE_FILTER, "origin", "master")), eq(true));
        verify(gitCommandExecutor, never()).execute(any(), eq(List.of("git", "fetch", BLOB_NONE_FILTER, "origin", "master")),
                eq(false));
        verify(gitCommandExecutor).execute(any(), eq(List.of("git", "checkout", "-B", "master", "origin/master")),
                eq(true));
        verify(gitCommandExecutor, never()).execute(any(), eq(List.of("git", "merge", "--no-ff", "--no-commit",
                "base-head")), eq(false));
    }

    @Test
    public void testResolveRemoteBranchCommit_fetchesWithBlobNoneFilter() {
        // 准备参数
        ReflectionTestUtils.setField(gitWorkspaceService, "workspaceRoot", "/tmp/gone-devops/git-workspaces");
        when(gitCommandExecutor.execute(any(), any(), anyBoolean()))
                .thenReturn(new GitCommandResult(0, ""));
        when(gitCommandExecutor.execute(any(), eq(List.of("git", "rev-parse", "origin/26/6/test")), eq(true)))
                .thenReturn(new GitCommandResult(0, "item-head\n"));

        // 调用
        String commitSha = gitWorkspaceService.resolveRemoteBranchCommit("run-1", "26/6/test");

        // 断言：只过滤 blob，commit 对象仍会获取，满足 CodeMerge 需要本地有该 commit 的约束
        assertEquals("item-head", commitSha);
        verify(gitCommandExecutor).execute(any(),
                eq(List.of("git", "fetch", BLOB_NONE_FILTER, "origin", "26/6/test")), eq(true));
    }

    @Test
    public void testMerge_successWithoutStagedChanges() {
        // 准备参数
        ReflectionTestUtils.setField(gitWorkspaceService, "workspaceRoot", "/tmp/gone-devops/git-workspaces");
        Path workspace = Path.of("/tmp/gone-devops/git-workspaces", "run-1").normalize();
        when(gitCommandExecutor.execute(eq(workspace),
                eq(List.of("git", "merge", "--no-ff", "--no-commit", "change-sha")), eq(false)))
                .thenReturn(new GitCommandResult(0, "Already up to date."));
        when(gitCommandExecutor.execute(eq(workspace),
                eq(List.of("git", "diff", "--cached", "--quiet")), eq(false)))
                .thenReturn(new GitCommandResult(0, ""));
        when(gitCommandExecutor.execute(eq(workspace), eq(List.of("git", "rev-parse", "HEAD")), eq(true)))
                .thenReturn(new GitCommandResult(0, "head-sha\n"));

        // 调用
        GitMergeResult result = gitWorkspaceService.merge("run-1", "change-sha", "Merge change");

        // 断言
        assertEquals(GitMergeResult.STATUS_SUCCESS, result.getStatus());
        assertEquals("head-sha", result.getMergeCommitSha());
        verify(gitCommandExecutor, never()).execute(eq(workspace), eq(List.of("git", "commit", "-m", "Merge change")),
                eq(true));
    }

    @Test
    public void testReadConflictContent_returnsCompleteContentAboveDefaultCommandLimit() throws Exception {
        Path workspace = prepareExternalWorkspace();
        String content = "content-line\n".repeat(1000);
        Files.writeString(workspace.resolve("src/App.java"), content);
        GitConflictDescriptor conflict = buildConflict("base", "ours", "theirs");
        mockBlob(workspace, "base", content);
        mockBlob(workspace, "ours", content);
        mockBlob(workspace, "theirs", content);

        GitConflictContent result = gitWorkspaceService.readConflictContent("run-1", conflict);

        assertEquals(content, result.getBaseContent());
        assertEquals(content, result.getOursContent());
        assertEquals(content, result.getTheirsContent());
        assertEquals(content, result.getWorkingContent());
        assertEquals(content, result.getResultContent());
        assertFalse(result.getContentTooLarge());
    }

    @Test
    public void testReadConflictContent_returnsContentAtFormerInlineBoundary() throws Exception {
        Path workspace = prepareExternalWorkspace();
        String content = "x".repeat(512 * 1024);
        GitConflictDescriptor conflict = buildConflict("base", null, null);
        mockBlob(workspace, "base", content);

        GitConflictContent result = gitWorkspaceService.readConflictContent("run-1", conflict);

        assertEquals(content, result.getBaseContent());
        assertFalse(result.getContentTooLarge());
        verify(gitCommandExecutor).execute(eq(workspace), eq(List.of("git", "cat-file", "blob", "base")),
                eq(true));
    }

    @Test
    public void testReadConflictContent_returnsBlobAboveFormerInlineLimit() throws Exception {
        Path workspace = prepareExternalWorkspace();
        String content = "x".repeat(512 * 1024 + 1);
        GitConflictDescriptor conflict = buildConflict("base", null, null);
        mockBlob(workspace, "base", content);

        GitConflictContent result = gitWorkspaceService.readConflictContent("run-1", conflict);

        assertEquals(content, result.getBaseContent());
        assertFalse(result.getContentTooLarge());
    }

    @Test
    public void testReadConflictContent_returnsWorkingFileAboveFormerInlineLimit() throws Exception {
        Path workspace = prepareExternalWorkspace();
        String content = "x".repeat(512 * 1024 + 1);
        Files.writeString(workspace.resolve("src/App.java"), content);
        GitConflictDescriptor conflict = buildConflict(null, null, null);

        GitConflictContent result = gitWorkspaceService.readConflictContent("run-1", conflict);

        assertEquals(content, result.getWorkingContent());
        assertEquals(content, result.getResultContent());
        assertFalse(result.getContentTooLarge());
    }

    @Test
    public void testIsConflictWorkspaceValid_checksUnmergedEntryAndBlobs() throws Exception {
        Path workspace = prepareExternalWorkspace();
        GitConflictDescriptor conflict = buildConflict("base", "ours", "theirs");
        when(gitCommandExecutor.execute(eq(workspace), any(), eq(false)))
                .thenReturn(new GitCommandResult(0, ""));
        when(gitCommandExecutor.execute(eq(workspace),
                eq(List.of("git", "ls-files", "-u", "--", "src/App.java")), eq(false)))
                .thenReturn(new GitCommandResult(0, conflictStages("base", "ours", "theirs")));

        assertTrue(gitWorkspaceService.isConflictWorkspaceValid("run-1", conflict));
        verify(gitCommandExecutor).execute(eq(workspace),
                eq(List.of("git", "cat-file", "-e", "base^{blob}")), eq(false));
        verify(gitCommandExecutor).execute(eq(workspace),
                eq(List.of("git", "cat-file", "-e", "ours^{blob}")), eq(false));
        verify(gitCommandExecutor).execute(eq(workspace),
                eq(List.of("git", "cat-file", "-e", "theirs^{blob}")), eq(false));
    }

    @Test
    public void testIsConflictWorkspaceValid_returnsFalseWithoutUnmergedEntry() throws Exception {
        Path workspace = prepareExternalWorkspace();
        GitConflictDescriptor conflict = buildConflict("base", "ours", "theirs");
        when(gitCommandExecutor.execute(eq(workspace),
                eq(List.of("git", "ls-files", "-u", "--", "src/App.java")), eq(false)))
                .thenReturn(new GitCommandResult(0, ""));

        assertFalse(gitWorkspaceService.isConflictWorkspaceValid("run-1", conflict));
        verify(gitCommandExecutor, never()).execute(eq(workspace),
                eq(List.of("git", "cat-file", "-e", "base^{blob}")), eq(false));
    }

    @Test
    public void testIsConflictWorkspaceValid_returnsFalseForMissingBlob() throws Exception {
        Path workspace = prepareExternalWorkspace();
        GitConflictDescriptor conflict = buildConflict("base", "ours", "theirs");
        when(gitCommandExecutor.execute(eq(workspace),
                eq(List.of("git", "ls-files", "-u", "--", "src/App.java")), eq(false)))
                .thenReturn(new GitCommandResult(0, conflictStages("base", "ours", "theirs")));
        when(gitCommandExecutor.execute(eq(workspace),
                eq(List.of("git", "cat-file", "-e", "base^{blob}")), eq(false)))
                .thenReturn(new GitCommandResult(0, ""));
        when(gitCommandExecutor.execute(eq(workspace),
                eq(List.of("git", "cat-file", "-e", "ours^{blob}")), eq(false)))
                .thenReturn(new GitCommandResult(128, "missing"));

        assertFalse(gitWorkspaceService.isConflictWorkspaceValid("run-1", conflict));
        verify(gitCommandExecutor, never()).execute(eq(workspace),
                eq(List.of("git", "cat-file", "-e", "theirs^{blob}")), eq(false));
    }

    @Test
    public void testIsConflictWorkspaceValid_returnsFalseWhenPersistedConflictWasReplaced() throws Exception {
        Path workspace = prepareExternalWorkspace();
        GitConflictDescriptor conflict = buildConflict("base", "ours", "theirs");
        when(gitCommandExecutor.execute(eq(workspace),
                eq(List.of("git", "ls-files", "-u", "--", "src/App.java")), eq(false)))
                .thenReturn(new GitCommandResult(0, conflictStages("new-base", "new-ours", "new-theirs")));

        assertFalse(gitWorkspaceService.isConflictWorkspaceValid("run-1", conflict));
        verify(gitCommandExecutor, never()).execute(eq(workspace),
                eq(List.of("git", "cat-file", "-e", "base^{blob}")), eq(false));
    }

    @Test
    public void testIsConflictWorkspaceValid_returnsFalseForMissingRepository() throws Exception {
        Path workspace = tempDir.resolve("workspace-without-git");
        Files.createDirectories(workspace);
        gitWorkspaceService.registerExternalWorkspace("run-1", workspace);

        assertFalse(gitWorkspaceService.isConflictWorkspaceValid("run-1", buildConflict("base", "ours", "theirs")));
        verify(gitCommandExecutor, never()).execute(any(), any(), anyBoolean());
    }

    private Path prepareExternalWorkspace() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace.resolve(".git"));
        Files.createDirectories(workspace.resolve("src"));
        gitWorkspaceService.registerExternalWorkspace("run-1", workspace);
        return workspace.normalize();
    }

    private GitConflictDescriptor buildConflict(String base, String ours, String theirs) {
        GitConflictDescriptor conflict = new GitConflictDescriptor();
        conflict.setFilePath("src/App.java");
        conflict.setBaseBlobSha(base);
        conflict.setOursBlobSha(ours);
        conflict.setTheirsBlobSha(theirs);
        return conflict;
    }

    private void mockBlob(Path workspace, String sha, String content) {
        when(gitCommandExecutor.execute(eq(workspace), eq(List.of("git", "cat-file", "blob", sha)), eq(true)))
                .thenReturn(new GitCommandResult(0, content));
    }

    private String conflictStages(String base, String ours, String theirs) {
        return "100644 " + base + " 1\tsrc/App.java\n"
                + "100644 " + ours + " 2\tsrc/App.java\n"
                + "100644 " + theirs + " 3\tsrc/App.java";
    }

}
