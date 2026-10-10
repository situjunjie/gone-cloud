package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.build.LogSink;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Routes commands using the runtime that was actually created for this job. */
@Component
@Primary
public class RoutingPipelineCommandExecutor implements PipelineCommandExecutor {

    private final DockerPipelineCommandExecutor dockerExecutor;
    private final K8sCommandExecutor kubernetesExecutor;

    public RoutingPipelineCommandExecutor(DockerPipelineCommandExecutor dockerExecutor,
                                         K8sCommandExecutor kubernetesExecutor) {
        this.dockerExecutor = dockerExecutor;
        this.kubernetesExecutor = kubernetesExecutor;
    }

    @Override
    public ExecResult exec(PipelineCommandContext ctx, String script, LogSink sink) {
        if (ctx == null || ctx.getRuntime() == null) {
            throw new IllegalArgumentException("Runtime cannot be null");
        }
        String type = ctx.getRuntime().getRuntimeType();
        return switch (type) {
            case "KUBERNETES" -> kubernetesExecutor.exec(ctx, script, sink);
            case "DOCKER" -> dockerExecutor.exec(ctx, script, sink);
            default -> throw new IllegalArgumentException("Unknown runtime type: " + type);
        };
    }

    @Override
    public void cancel(String runId) {
        if (runId == null) {
            return;
        }
        // A run may have simultaneous jobs on both backends.
        try {
            dockerExecutor.cancel(runId);
        } finally {
            kubernetesExecutor.cancel(runId);
        }
    }
}
