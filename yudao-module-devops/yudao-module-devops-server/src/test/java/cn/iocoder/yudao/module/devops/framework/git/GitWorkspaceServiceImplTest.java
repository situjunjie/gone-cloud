package cn.iocoder.yudao.module.devops.framework.git;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

}
