package cn.iocoder.yudao.module.devops.framework.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GitCheckoutScriptTest {
    @TempDir
    Path root;

    @Test
    void checkoutRefreshesNonEmptyWorkspaceAndFetchesExplicitRef() throws Exception {
        Path repository = Files.createDirectory(root.resolve("source repository"));
        git(repository, "init", "-b", "main");
        git(repository, "config", "user.email", "test@example.com");
        git(repository, "config", "user.name", "Test");
        Files.writeString(repository.resolve("file"), "first");
        git(repository, "add", ".");
        git(repository, "commit", "-m", "first");
        String first = git(repository, "rev-parse", "HEAD").trim();
        Path workspace = Files.createDirectories(root.resolve("workspace")).toRealPath();
        Files.createDirectories(workspace.resolve("reports"));
        Files.writeString(workspace.resolve("reports/report"), "keep");
        checkout(repository, workspace, null);
        Files.writeString(repository.resolve("file"), "second");
        git(repository, "commit", "-am", "second");
        checkout(repository, workspace, null);
        assertEquals("second", Files.readString(workspace.resolve("file")));
        checkout(repository, workspace, first);
        assertEquals("first", Files.readString(workspace.resolve("file")));
        assertEquals("keep", Files.readString(workspace.resolve("reports/report")));
        assertFalse(Files.exists(workspace.resolve(".git-credentials")));
    }

    @Test
    void checkoutRejectsEscapingPathsAndOptionRefs() {
        assertThrows(IllegalArgumentException.class,
                () -> GitCheckoutScript.build("https://example.com/repo", "main", null, 1, null, "../outside"));
        assertThrows(IllegalArgumentException.class,
                () -> GitCheckoutScript.build("https://example.com/repo", "--help", null, 1, null, "."));
    }

    private void checkout(Path repository, Path workspace, String ref) throws Exception {
        String script = GitCheckoutScript.build(repository.toString(), "main", ref, 1, null, ".")
                .replace("/workspace", workspace.toString());
        execute(List.of("sh", "-c", script), workspace);
    }

    private String git(Path path, String... args) throws Exception {
        var command = new java.util.ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        return execute(command, path);
    }

    private String execute(List<String> command, Path path) throws Exception {
        Process process = new ProcessBuilder(command).directory(path.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }
}
