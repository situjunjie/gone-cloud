package cn.iocoder.yudao.module.devops.framework.git;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesEnvironmentConfig;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandContext;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandExecutor;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntimeManagerFactory;
import cn.iocoder.yudao.module.devops.service.environment.EnvironmentService;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.devops.framework.pipeline.runtime.ShellQuoting.quote;

/** Routes Git commands and conflict files to their owning Pod workspace. */
@Component
public class RuntimeGitWorkspaceSupport {
    private static final int MAX_OUTPUT_CHARS = 8 * 1024 * 1024;
    @Resource
    private PipelineJobRuntimeManagerFactory runtimeManagerFactory;
    @Resource
    private EnvironmentService environmentService;
    @Resource
    private RepositoryProviderService repositoryProviderService;
    private final Map<Path, GitWorkspaceRuntimeRef> workspaces = new ConcurrentHashMap<>();

    public Path register(GitWorkspaceRuntimeRef ref) {
        if (ref == null || ref.getEnvironmentId() == null || ref.getNamespace() == null || ref.getPodName() == null
                || !ref.getNamespace().matches("[a-z0-9]([-a-z0-9]*[a-z0-9])?")
                || !ref.getPodName().matches("[a-z0-9]([-a-z0-9]*[a-z0-9])?")) {
            throw new GitCommandException("Incomplete Pod workspace identity", null);
        }
        Path path = Path.of("/__pipeline_pods", ref.getEnvironmentId().toString(), ref.getNamespace(), ref.getPodName());
        workspaces.put(path, ref);
        return path;
    }

    public boolean contains(Path path) {
        return path != null && workspaces.keySet().stream().anyMatch(path::startsWith);
    }

    public void unregister(Path path) {
        workspaces.remove(path);
    }

    public GitCommandResult git(Path path, List<String> args, boolean check) {
        return execute(path, args.stream().map(arg -> quote(arg)).collect(Collectors.joining(" ")), check);
    }

    public boolean exists(Path path) {
        return execute(root(path), "test -e " + quote(remotePath(path)), false).isSuccess();
    }

    public void mkdir(Path path) {
        execute(root(path), "mkdir -p -- " + quote(remotePath(path)), true);
    }

    public byte[] read(Path path) {
        String file = quote(remotePath(path));
        String script = "set -e\nfile=" + file + "\ntest ! -L \"$file\"\n"
                + "case $(cd \"$(dirname -- \"$file\")\" && pwd -P) in /workspace|/workspace/*) ;; *) exit 1 ;; esac\n"
                + "base64 < \"$file\"";
        String output = execute(root(path), script, true).getOutput();
        return Base64.getMimeDecoder().decode(output);
    }

    public byte[] readBlob(Path path, String blobSha) {
        String script = "set -e\ntmp=$(mktemp)\ntrap 'rm -f -- \"$tmp\"' EXIT HUP INT TERM\n"
                + "git cat-file blob " + quote(blobSha) + " > \"$tmp\"\nbase64 < \"$tmp\"";
        return Base64.getMimeDecoder().decode(execute(path, script, true).getOutput());
    }

    public void write(Path path, byte[] content) {
        String encoded = Base64.getEncoder().encodeToString(content);
        String remote = remotePath(path);
        // Reject a symlink target or parent that could write outside the mounted repository.
        String script = "set -e\nfile=" + quote(remote) + "\n"
                + "parent=$(dirname -- \"$file\")\nmkdir -p -- \"$parent\"\n"
                + "case $(cd \"$parent\" && pwd -P) in /workspace|/workspace/*) ;; *) exit 1 ;; esac\n"
                + "test ! -L \"$file\"\nprintf '%s' " + quote(encoded) + " | base64 -d > \"$file\"";
        execute(root(path), script, true);
    }

    private Path root(Path path) {
        return workspaces.keySet().stream().filter(path::startsWith).findFirst()
                .orElseThrow(() -> new GitCommandException("Pod workspace is not registered", null));
    }

    private String remotePath(Path path) {
        return Path.of("/workspace").resolve(root(path).relativize(path)).toString();
    }

    private GitCommandResult execute(Path path, String script, boolean check) {
        GitWorkspaceRuntimeRef ref = workspaces.get(root(path));
        EnvironmentDO environment = environmentService.getEnvironment(ref.getEnvironmentId());
        if (environment == null) {
            throw new GitCommandException("Build environment no longer exists", null);
        }
        KubernetesEnvironmentConfig config = JsonUtils.parseObject(environment.getInfraConfig(), KubernetesEnvironmentConfig.class);
        PipelineJobRuntime runtime = PipelineJobRuntime.builder().runtimeType("KUBERNETES")
                .runtimeId(ref.getPodName()).workspace(Path.of("/workspace"))
                .metadata(Map.of("namespace", ref.getNamespace(), "kubeconfig", config.getKubeconfig())).build();
        StringBuilder output = new StringBuilder();
        Map<String, String> environmentVariables = new java.util.HashMap<>();
        if (ref.getRepositoryProviderId() != null) {
            var provider = repositoryProviderService.getRepositoryProvider(ref.getRepositoryProviderId());
            if (provider == null || provider.getAccessToken() == null) {
                throw new GitCommandException("Repository credentials no longer available", null);
            }
            environmentVariables.put("GIT_PASSWORD", provider.getAccessToken());
            script = "set -eu\nexport GIT_TERMINAL_PROMPT=0\naskpass=$(mktemp /tmp/pipeline-git.XXXXXX)\n"
                    + "trap 'rm -f -- \"$askpass\"' EXIT HUP INT TERM\n"
                    + "printf '%s\\n' '#!/bin/sh' 'case \"$1\" in *Username*) printf \"%s\\n\" oauth2 ;; *) printf \"%s\\n\" \"$GIT_PASSWORD\" ;; esac' > \"$askpass\"\n"
                    + "chmod 700 \"$askpass\"\nexport GIT_ASKPASS=\"$askpass\"\n"
                    + "export GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=credential.helper GIT_CONFIG_VALUE_0=''\n"
                    + "(\n" + script + "\n)";
        }
        PipelineCommandExecutor executor = runtimeManagerFactory.selectCommandExecutor(runtime);
        ExecResult result = executor.exec(PipelineCommandContext.builder().runtime(runtime)
                        .workingDirectory(remotePath(path)).env(environmentVariables).timeoutSeconds(300).build(), script,
                (stream, line) -> {
                    synchronized (output) {
                        if (output.length() + line.length() > MAX_OUTPUT_CHARS) {
                            throw new GitCommandException("Git output exceeds the workspace limit", null);
                        }
                        if ("STDOUT".equalsIgnoreCase(stream)) {
                            output.append(line).append('\n');
                        }
                    }
                });
        GitCommandResult gitResult = new GitCommandResult(result.getExitCode(), output.toString());
        if (check && !gitResult.isSuccess()) {
            throw new GitCommandException("Git command in build Pod failed", null);
        }
        return gitResult;
    }
}
