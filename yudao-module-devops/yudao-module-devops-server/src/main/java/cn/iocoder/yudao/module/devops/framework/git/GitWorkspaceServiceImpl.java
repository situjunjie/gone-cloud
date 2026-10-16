package cn.iocoder.yudao.module.devops.framework.git;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于本机 Git 命令的工作区服务。
 */
@Component
public class GitWorkspaceServiceImpl implements GitWorkspaceService {

    @Value("${yudao.devops.git-workspace-root:${java.io.tmpdir}/gone-devops/git-workspaces}")
    private String workspaceRoot;

    /**
     * 部分克隆过滤：只下载 commit/tree 对象，blob 在 checkout / merge 需要时惰性获取。
     *
     * <p>用于 CodeMerge 的 fetch。不能用 shallow clone（{@code --depth}）代替：
     * {@code git merge --no-ff --no-commit <sha>} 依赖共同祖先和完整侧分支，
     * shallow 边界截断历史后会报 {@code refusing to merge unrelated histories}。
     *
     * <p>服务端不支持 filter 时 git 仅告警并退化为全量 fetch，不会失败。
     */
    private static final String FETCH_BLOB_NONE_FILTER = "--filter=blob:none";

    @Resource
    private GitCommandExecutor gitCommandExecutor;

    private final ConcurrentMap<String, Path> externalWorkspaces = new ConcurrentHashMap<>();

    @Override
    public GitWorkspacePrepareResult prepareWorkspace(Long pipelineRunId, String repoUrl, String accessToken,
                                                      String baseBranch, String deployBranch) {
        String workspaceKey = "run-" + pipelineRunId + "-" + UUID.randomUUID();
        Path workspace = workspacePath(workspaceKey);
        return prepareManagedWorkspace(workspaceKey, workspace, repoUrl, accessToken, baseBranch, deployBranch);
    }

    @Override
    public GitWorkspacePrepareResult prepareWorkspace(String workspaceKey, Path workspace, String repoUrl, String accessToken,
                                                      String baseBranch, String deployBranch) {
        if (StrUtil.isBlank(workspaceKey) || workspace == null) {
            throw new GitCommandException("External workspace key and path are required", null);
        }
        registerExternalWorkspace(workspaceKey, workspace);
        try {
            return prepareWorkspaceInPlace(workspaceKey, workspace.normalize(), repoUrl, accessToken, baseBranch, deployBranch);
        } catch (RuntimeException ex) {
            externalWorkspaces.remove(workspaceKey);
            throw ex;
        }
    }

    @Override
    public void registerExternalWorkspace(String workspaceKey, Path workspace) {
        if (StrUtil.isBlank(workspaceKey) || workspace == null) {
            throw new GitCommandException("External workspace key and path are required", null);
        }
        externalWorkspaces.put(workspaceKey, workspace.normalize());
    }

    private GitWorkspacePrepareResult prepareManagedWorkspace(String workspaceKey, Path workspace, String repoUrl,
                                                              String accessToken, String baseBranch,
                                                              String deployBranch) {
        try {
            Files.createDirectories(workspace.getParent());
        } catch (IOException ex) {
            throw new GitCommandException("Create workspace failed: " + ex.getMessage(), null);
        }

        try {
            git(null, List.of("git", "clone", "--no-tags", buildAuthenticatedRepoUrl(repoUrl, accessToken),
                    workspace.toString()), true);
            return prepareWorkspaceInPlace(workspaceKey, workspace, null, null, baseBranch, deployBranch);
        } catch (RuntimeException ex) {
            cleanup(workspaceKey);
            throw ex;
        }
    }

    private GitWorkspacePrepareResult prepareWorkspaceInPlace(String workspaceKey, Path workspace, String repoUrl,
                                                              String accessToken, String baseBranch,
                                                              String deployBranch) {
        try {
            Files.createDirectories(workspace);
        } catch (IOException ex) {
            throw new GitCommandException("Create workspace failed: " + ex.getMessage(), null);
        }
        if (StrUtil.isNotBlank(repoUrl)) {
            ensureGitRepository(workspace, repoUrl, accessToken);
        }
        git(workspace, List.of("git", "config", "user.email", "devops@gone.local"), true);
        git(workspace, List.of("git", "config", "user.name", "Gone DevOps"), true);
        git(workspace, List.of("git", "fetch", FETCH_BLOB_NONE_FILTER, "origin", baseBranch), true);
        String baseRemoteCommitSha = revParse(workspaceKey, "origin/" + baseBranch);
        boolean sameBranch = deployBranch.equals(baseBranch);
        // 尝试fetch deployBranch，如果不存在（新创建的部署分支）则返回false
        boolean deployBranchExisted = sameBranch || fetchBranch(workspace, deployBranch);
        resetWorktree(workspace);

        if (deployBranchExisted && !sameBranch) {
            // deployBranch远程存在，基于远程deployBranch创建本地分支，然后合并最新的base
            git(workspace, List.of("git", "checkout", "-B", deployBranch, "origin/" + deployBranch), true);
            GitMergeResult baseMergeResult = merge(workspaceKey, baseRemoteCommitSha,
                    "Merge latest base branch " + baseBranch + " into deploy branch " + deployBranch);
            if (!GitMergeResult.STATUS_SUCCESS.equals(baseMergeResult.getStatus())) {
                throw new GitCommandException("Merge latest base branch failed", baseMergeResult.getOutput());
            }
        } else {
            // deployBranch远程不存在，或者与baseBranch相同，直接从baseBranch创建
            git(workspace, List.of("git", "checkout", "-B", deployBranch, "origin/" + baseBranch), true);
        }
        String preparedCommitSha = revParse(workspaceKey, "HEAD");

        GitWorkspacePrepareResult result = new GitWorkspacePrepareResult();
        result.setWorkspaceKey(workspaceKey);
        result.setBaseCommitSha(preparedCommitSha);
        result.setBaseBranch(baseBranch);
        result.setDeployBranch(deployBranch);
        result.setBaseRemoteCommitSha(baseRemoteCommitSha);
        result.setPreparedCommitSha(preparedCommitSha);
        result.setDeployBranchExisted(deployBranchExisted);
        return result;
    }

    @Override
    public String resolveRemoteBranchCommit(String workspaceKey, String branchName) {
        Path workspace = workspacePath(workspaceKey);
        git(workspace, List.of("git", "fetch", FETCH_BLOB_NONE_FILTER, "origin", branchName), true);
        return revParse(workspaceKey, "origin/" + branchName);
    }

    @Override
    public GitMergeResult merge(String workspaceKey, String commitSha, String message) {
        Path workspace = workspacePath(workspaceKey);
        GitCommandResult mergeResult = git(workspace, List.of("git", "merge", "--no-ff", "--no-commit", commitSha),
                false);
        GitMergeResult result = new GitMergeResult();
        result.setOutput(mergeResult.getOutput());
        if (mergeResult.isSuccess()) {
            result.setStatus(GitMergeResult.STATUS_SUCCESS);
            if (!hasStagedChanges(workspace)) {
                result.setMergeCommitSha(revParse(workspaceKey, "HEAD"));
                result.setConflicts(List.of());
                return result;
            }
            git(workspace, List.of("git", "commit", "-m", message), true);
            result.setMergeCommitSha(revParse(workspaceKey, "HEAD"));
            result.setConflicts(List.of());
            return result;
        }
        List<GitConflictDescriptor> conflicts = extractConflicts(workspaceKey);
        if (conflicts.isEmpty()) {
            throw new GitCommandException("Git merge failed without conflicts", mergeResult.getOutput());
        }
        result.setStatus(GitMergeResult.STATUS_CONFLICTING);
        result.setConflicts(conflicts);
        return result;
    }

    @Override
    public boolean isConflictWorkspaceValid(String workspaceKey, GitConflictDescriptor conflict) {
        if (StrUtil.isBlank(workspaceKey) || conflict == null || StrUtil.isBlank(conflict.getFilePath())) {
            return false;
        }
        Path workspace = workspacePath(workspaceKey);
        if (!Files.isDirectory(workspace) || !Files.exists(workspace.resolve(".git"))) {
            return false;
        }
        Path conflictFile = workspace.resolve(conflict.getFilePath()).normalize();
        if (!conflictFile.startsWith(workspace.normalize())) {
            return false;
        }
        GitCommandResult unmergedFiles = git(workspace,
                List.of("git", "ls-files", "-u", "--", conflict.getFilePath()), false);
        if (!unmergedFiles.isSuccess() || StrUtil.isBlank(unmergedFiles.getOutput())) {
            return false;
        }
        Map<Integer, String> currentStages = parseConflictStages(unmergedFiles.getOutput());
        if (currentStages.isEmpty()
                || !isConflictStageMatching(currentStages, 1, conflict.getBaseBlobSha())
                || !isConflictStageMatching(currentStages, 2, conflict.getOursBlobSha())
                || !isConflictStageMatching(currentStages, 3, conflict.getTheirsBlobSha())) {
            return false;
        }
        return isBlobValid(workspace, conflict.getBaseBlobSha())
                && isBlobValid(workspace, conflict.getOursBlobSha())
                && isBlobValid(workspace, conflict.getTheirsBlobSha());
    }

    @Override
    public GitConflictContent readConflictContent(String workspaceKey, GitConflictDescriptor conflict) {
        GitConflictContent content = new GitConflictContent();
        content.setBaseContent(readBlob(workspaceKey, conflict.getBaseBlobSha()));
        content.setOursContent(readBlob(workspaceKey, conflict.getOursBlobSha()));
        content.setTheirsContent(readBlob(workspaceKey, conflict.getTheirsBlobSha()));
        content.setWorkingContent(readWorkingFile(workspaceKey, conflict.getFilePath()));
        content.setResultContent(content.getWorkingContent());
        content.setContentTooLarge(false);
        return content;
    }

    @Override
    public String continueMerge(String workspaceKey, List<GitFileResolution> resolutions, String message) {
        Path workspace = workspacePath(workspaceKey);
        for (GitFileResolution resolution : resolutions) {
            writeWorkingFile(workspace, resolution.getFilePath(), resolution.getResolvedContent());
            git(workspace, List.of("git", "add", "--", resolution.getFilePath()), true);
        }
        git(workspace, List.of("git", "commit", "-m", message), true);
        return revParse(workspaceKey, "HEAD");
    }

    @Override
    public void abortMerge(String workspaceKey) {
        Path workspace = workspacePath(workspaceKey);
        // 工作区可能已在代码合并完成后被清理（例如部署阶段取消流水线），此时无需中止合并
        if (!Files.exists(workspace)) {
            return;
        }
        git(workspace, List.of("git", "merge", "--abort"), false);
    }

    @Override
    public void pushDeployBranch(String workspaceKey, String deployBranch) {
        git(workspacePath(workspaceKey), List.of("git", "push", "origin", "HEAD:refs/heads/" + deployBranch), true);
    }

    @Override
    public void cleanup(String workspaceKey) {
        Path external = externalWorkspaces.remove(workspaceKey);
        if (external != null) {
            return;
        }
        Path workspace = workspacePath(workspaceKey);
        if (!Files.exists(workspace)) {
            return;
        }
        try (var stream = Files.walk(workspace)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private List<GitConflictDescriptor> extractConflicts(String workspaceKey) {
        Path workspace = workspacePath(workspaceKey);
        GitCommandResult result = git(workspace, List.of("git", "ls-files", "-u"), true);
        Map<String, Map<Integer, String>> stages = new HashMap<>();
        for (String line : result.getOutput().split("\\R")) {
            if (StrUtil.isBlank(line)) {
                continue;
            }
            String[] parts = line.split("\\s+", 4);
            if (parts.length < 4) {
                continue;
            }
            stages.computeIfAbsent(parts[3], ignored -> new HashMap<>())
                    .put(Integer.parseInt(parts[2]), parts[1]);
        }
        List<GitConflictDescriptor> conflicts = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, String>> entry : stages.entrySet()) {
            conflicts.add(buildConflict(workspace, entry.getKey(), entry.getValue()));
        }
        return conflicts;
    }

    private GitConflictDescriptor buildConflict(Path workspace, String filePath, Map<Integer, String> stages) {
        GitConflictDescriptor conflict = new GitConflictDescriptor();
        conflict.setFilePath(filePath);
        conflict.setBaseBlobSha(stages.get(1));
        conflict.setOursBlobSha(stages.get(2));
        conflict.setTheirsBlobSha(stages.get(3));
        conflict.setConflictType(resolveConflictType(stages));
        fillFileMetadata(workspace, conflict);
        if (!"TEXT".equals(conflict.getConflictType()) || !Boolean.TRUE.equals(conflict.getText())) {
            conflict.setUnsupportedReason("当前冲突类型暂不支持在线解决");
        }
        return conflict;
    }

    private String resolveConflictType(Map<Integer, String> stages) {
        if (!stages.containsKey(1) && stages.containsKey(2) && stages.containsKey(3)) {
            return "ADD_ADD";
        }
        if (!stages.containsKey(2) || !stages.containsKey(3)) {
            return "DELETE_MODIFY";
        }
        return "TEXT";
    }

    private void fillFileMetadata(Path workspace, GitConflictDescriptor conflict) {
        Path file = workspace.resolve(conflict.getFilePath()).normalize();
        try {
            long size = Files.exists(file) ? Files.size(file) : 0L;
            conflict.setContentSize(size);
            if (!Files.exists(file)) {
                conflict.setText(false);
                return;
            }
            byte[] bytes = Files.readAllBytes(file);
            boolean text = isText(bytes);
            conflict.setText(text);
            if (text) {
                String content = new String(bytes, StandardCharsets.UTF_8);
                conflict.setCharset(StandardCharsets.UTF_8.name());
                conflict.setLineCount(content.split("\\R", -1).length);
            }
        } catch (IOException ex) {
            conflict.setText(false);
        }
    }

    private boolean isText(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return false;
            }
        }
        return true;
    }

    private boolean hasStagedChanges(Path workspace) {
        GitCommandResult diffResult = git(workspace, List.of("git", "diff", "--cached", "--quiet"), false);
        if (diffResult.getExitCode() == 0) {
            return false;
        }
        if (diffResult.getExitCode() == 1) {
            return true;
        }
        throw new GitCommandException("Git diff cached failed", diffResult.getOutput());
    }

    private boolean isBlobValid(Path workspace, String blobSha) {
        if (StrUtil.isBlank(blobSha)) {
            return true;
        }
        return git(workspace, List.of("git", "cat-file", "-e", blobSha + "^{blob}"), false).isSuccess();
    }

    private Map<Integer, String> parseConflictStages(String output) {
        Map<Integer, String> stages = new HashMap<>();
        for (String line : output.split("\\R")) {
            if (StrUtil.isBlank(line)) {
                continue;
            }
            String[] parts = line.split("\\s+", 4);
            if (parts.length < 4) {
                return Map.of();
            }
            try {
                stages.put(Integer.parseInt(parts[2]), parts[1]);
            } catch (NumberFormatException ex) {
                return Map.of();
            }
        }
        return stages;
    }

    private boolean isConflictStageMatching(Map<Integer, String> stages, int stage, String expectedBlobSha) {
        return StrUtil.isBlank(expectedBlobSha) ? !stages.containsKey(stage)
                : expectedBlobSha.equals(stages.get(stage));
    }

    private String readBlob(String workspaceKey, String blobSha) {
        if (StrUtil.isBlank(blobSha)) {
            return null;
        }
        return git(workspacePath(workspaceKey), List.of("git", "cat-file", "blob", blobSha), true).getOutput();
    }

    private String readWorkingFile(String workspaceKey, String filePath) {
        Path workspace = workspacePath(workspaceKey).normalize();
        Path file = workspace.resolve(filePath).normalize();
        if (!file.startsWith(workspace)) {
            throw new GitCommandException("Invalid git file path", null);
        }
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new GitCommandException("Read working file failed: " + ex.getMessage(), null);
        }
    }

    private void writeWorkingFile(Path workspace, String filePath, String content) {
        Path file = workspace.resolve(filePath).normalize();
        if (!file.startsWith(workspace.normalize())) {
            throw new GitCommandException("Invalid git file path", null);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new GitCommandException("Write working file failed: " + ex.getMessage(), null);
        }
    }

    private String revParse(String workspaceKey, String ref) {
        return StrUtil.trim(git(workspacePath(workspaceKey), List.of("git", "rev-parse", ref), true).getOutput());
    }

    private GitCommandResult git(Path workDir, List<String> command, boolean checkExitCode) {
        return gitCommandExecutor.execute(workDir, command, checkExitCode);
    }

    private void ensureGitRepository(Path workspace, String repoUrl, String accessToken) {
        if (!Files.exists(workspace.resolve(".git"))) {
            git(workspace, List.of("git", "init"), true);
        }
        String authenticatedRepoUrl = buildAuthenticatedRepoUrl(repoUrl, accessToken);
        if (git(workspace, List.of("git", "remote", "get-url", "origin"), false).isSuccess()) {
            git(workspace, List.of("git", "remote", "set-url", "origin", authenticatedRepoUrl), true);
            return;
        }
        git(workspace, List.of("git", "remote", "add", "origin", authenticatedRepoUrl), true);
    }

    private void resetWorktree(Path workspace) {
        if (!Files.exists(workspace.resolve(".git"))) {
            return;
        }
        git(workspace, List.of("git", "merge", "--abort"), false);
        git(workspace, List.of("git", "reset", "--hard"), false);
    }

    private boolean fetchBranch(Path workspace, String branchName) {
        return git(workspace, List.of("git", "fetch", FETCH_BLOB_NONE_FILTER, "origin", branchName), false).isSuccess();
    }

    private Path workspacePath(String workspaceKey) {
        Path externalWorkspace = externalWorkspaces.get(workspaceKey);
        if (externalWorkspace != null) {
            return externalWorkspace;
        }
        return Path.of(workspaceRoot, workspaceKey).normalize();
    }

    private String buildAuthenticatedRepoUrl(String repoUrl, String accessToken) {
        if (StrUtil.isBlank(accessToken)) {
            return repoUrl;
        }
        URI uri = URI.create(repoUrl);
        String token = URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
        String path = StrUtil.nullToEmpty(uri.getRawPath());
        return uri.getScheme() + "://oauth2:" + token + "@" + uri.getAuthority() + path;
    }

}
