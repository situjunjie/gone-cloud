package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

/**
 * 流水线工作目录相关常量。
 */
public final class PipelineWorkspaceConstants {

    /**
     * run workspace 在容器内的挂载点。
     */
    public static final String CONTAINER_WORKSPACE = "/workspace";

    /**
     * {@link PipelineWorkspace#getMetadata()} 中描述共享存储卷内 run workspace 子路径的键。
     *
     * <p>取值按 {@code tenantId + appId + applicationEnvId + definitionId} 稳定派生，
     * 使同一应用环境的多次运行挂载到同一目录，从而复用已下载的 Git 对象库。
     */
    public static final String WORKSPACE_SUB_PATH_KEY = "workspaceSubPath";

    private PipelineWorkspaceConstants() {
    }

}
