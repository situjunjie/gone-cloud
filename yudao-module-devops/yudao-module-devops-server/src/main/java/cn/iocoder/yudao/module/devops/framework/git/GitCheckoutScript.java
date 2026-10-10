package cn.iocoder.yudao.module.devops.framework.git;

import java.nio.file.Path;

import static cn.iocoder.yudao.module.devops.framework.pipeline.runtime.ShellQuoting.quote;

/** In-place checkout suitable for a workspace containing platform directories. */
public final class GitCheckoutScript {
    private GitCheckoutScript() {
    }

    public static String build(String repository, String branch, String ref, int depth, String submodules, String path) {
        if (repository == null || repository.isBlank() || repository.startsWith("-") || depth < 0) {
            throw new IllegalArgumentException("Invalid checkout repository or depth");
        }
        validateRef(branch);
        validateRef(ref);
        Path relative = Path.of(path == null || path.isBlank() ? "." : path).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IllegalArgumentException("Checkout path must stay within /workspace");
        }
        String target = Path.of("/workspace").resolve(relative).normalize().toString();
        String fetchRef = ref != null && !ref.isBlank() ? ref : branch != null && !branch.isBlank() ? branch : "HEAD";
        StringBuilder script = new StringBuilder("set -eu\nexport GIT_TERMINAL_PROMPT=0\n");
        script.append("askpass=''\ntrap 'test -z \"$askpass\" || rm -f -- \"$askpass\"' EXIT HUP INT TERM\n")
                .append("if [ -n \"${GIT_PASSWORD:-}\" ]; then\n")
                .append("  askpass=$(mktemp /tmp/pipeline-git-askpass.XXXXXX)\n")
                .append("  printf '%s\\n' '#!/bin/sh' 'case \"$1\" in *Username*) printf \"%s\\n\" \"${GIT_USERNAME:-oauth2}\" ;; *) printf \"%s\\n\" \"$GIT_PASSWORD\" ;; esac' > \"$askpass\"\n")
                .append("  chmod 700 \"$askpass\"\n  export GIT_ASKPASS=\"$askpass\"\nfi\n")
                .append("mkdir -p -- ").append(quote(target)).append("\ncd ").append(quote(target)).append('\n')
                .append("case $(pwd -P) in /workspace|/workspace/*) ;; *) exit 1 ;; esac\n")
                .append("if [ ! -d .git ]; then git init; fi\n")
                .append("git config user.name \"${GIT_USER_NAME:-Pipeline Bot}\"\n")
                .append("git config user.email \"${GIT_USER_EMAIL:-bot@pipeline.local}\"\n")
                .append("git remote remove origin 2>/dev/null || true\ngit remote add origin ").append(quote(repository)).append('\n')
                .append("git -c credential.helper= fetch ");
        if (depth > 0) {
            script.append("--depth ").append(depth).append(' ');
        }
        script.append("origin ").append(quote(fetchRef)).append('\n');
        if ((ref == null || ref.isBlank()) && branch != null && !branch.isBlank()) {
            script.append("git checkout -f -B ").append(quote(branch)).append(" FETCH_HEAD\n");
        } else {
            script.append("git checkout -f --detach FETCH_HEAD\n");
        }
        if ("true".equals(submodules) || "recursive".equals(submodules)) {
            script.append("git -c credential.helper= submodule update --init")
                    .append("recursive".equals(submodules) ? " --recursive\n" : "\n");
        }
        script.append("git rev-parse HEAD\n");
        return script.toString();
    }

    private static void validateRef(String ref) {
        if (ref != null && (ref.startsWith("-") || ref.indexOf('\0') >= 0 || ref.indexOf('\n') >= 0)) {
            throw new IllegalArgumentException("Invalid checkout ref");
        }
    }
}
