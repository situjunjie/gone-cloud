package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.framework.config.DevopsConfig;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 流水线任务运行时管理器工厂。
 *
 * <p>根据 runs-on 配置或全局配置选择合适的运行时管理器。
 */
@Slf4j
@Component
public class PipelineJobRuntimeManagerFactory {

    @Resource
    @Qualifier("dockerPipelineJobRuntimeManager")
    private PipelineJobRuntimeManager dockerManager;

    @Resource
    @Qualifier("k8sPipelineJobRuntimeManager")
    private PipelineJobRuntimeManager k8sManager;

    @Resource
    private DevopsConfig devopsConfig;

    /**
     * 选择运行时管理器。
     *
     * @param job 可执行任务定义
     * @return 运行时管理器
     */
    public PipelineJobRuntimeManager selectManager(PipelineSpec.ExecutableJob job) {
        PipelineSpec.RunsOn runsOn = job.getRunsOn();
        String group = runsOn == null ? null : runsOn.getGroup();

        if (StrUtil.isBlank(group)) {
            // 使用默认配置
            return getDefaultManager();
        }

        // 根据 group 前缀判断
        if (group.startsWith("kubernetes/")) {
            log.debug("[selectManager][jobId({}) 使用 K8s 运行时管理器]", job.getJobId());
            return k8sManager;
        } else if (group.startsWith("local-docker/")) {
            log.debug("[selectManager][jobId({}) 使用 Docker 运行时管理器]", job.getJobId());
            return dockerManager;
        }

        throw new IllegalArgumentException("Unknown executor group: " + group);
    }

    /**
     * 获取默认运行时管理器。
     */
    private PipelineJobRuntimeManager getDefaultManager() {
        String defaultExecutor = devopsConfig.getBuild().getDefaultExecutor();

        return switch (StrUtil.blankToDefault(defaultExecutor, "DOCKER").toUpperCase()) {
            case "KUBERNETES" -> {
                log.debug("[getDefaultManager] 使用默认 K8s 运行时管理器");
                yield k8sManager;
            }
            case "DOCKER" -> {
                log.debug("[getDefaultManager] 使用默认 Docker 运行时管理器");
                yield dockerManager;
            }
            default -> {
                log.warn("[getDefaultManager] 未知的默认执行器类型: {}, 回退到 Docker", defaultExecutor);
                yield dockerManager;
            }
        };
    }

    public boolean isKubernetesJob(PipelineSpec.ExecutableJob job) {
        return selectManager(job) == k8sManager;
    }

    /** Clean resources owned by both backends after a run reaches a terminal state. */
    public void cleanupRun(cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO run) {
        dockerManager.cleanupRun(run);
        k8sManager.cleanupRun(run);
    }

    /**
     * 选择命令执行器。
     *
     * @param runtime 运行时对象
     * @return 命令执行器
     */
    public PipelineCommandExecutor selectCommandExecutor(PipelineJobRuntime runtime) {
        if (runtime == null) {
            throw new IllegalArgumentException("Runtime cannot be null");
        }

        String runtimeType = runtime.getRuntimeType();

        return switch (runtimeType) {
            case "KUBERNETES" -> getK8sCommandExecutor();
            case "DOCKER" -> getDockerCommandExecutor();
            default -> throw new IllegalArgumentException("Unknown runtime type: " + runtimeType);
        };
    }

    @Resource
    @Qualifier("k8sCommandExecutor")
    private K8sCommandExecutor k8sCommandExecutor;

    @Resource
    @Qualifier("dockerPipelineCommandExecutor")
    private DockerPipelineCommandExecutor dockerCommandExecutor;

    private PipelineCommandExecutor getK8sCommandExecutor() {
        return k8sCommandExecutor;
    }

    private PipelineCommandExecutor getDockerCommandExecutor() {
        return dockerCommandExecutor;
    }

}
