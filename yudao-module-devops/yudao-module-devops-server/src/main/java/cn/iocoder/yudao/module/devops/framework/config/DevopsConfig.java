package cn.iocoder.yudao.module.devops.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * DevOps 全局配置。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DevopsConfig {

    /**
     * 构建配置。
     */
    private BuildConfig build = new BuildConfig();

    /**
     * 构建配置。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BuildConfig {

        /**
         * 默认执行器类型：LOCAL, DOCKER, KUBERNETES
         */
        private String defaultExecutor = "DOCKER";

        /**
         * 构建集群。
         */
        private BuildClusterConfig cluster = new BuildClusterConfig();

    }

    /**
     * 构建集群配置。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BuildClusterConfig {

        /**
         * DevOps Kubernetes 环境编号。
         */
        private Long environmentId;

        /**
         * 构建任务命名空间。
         */
        private String namespace = "devops-build";

        /**
         * 默认构建镜像。
         */
        private String defaultImage = "maven:3.8-openjdk-17";

        /**
         * Kaniko 执行器镜像。
         */
        private String kanikoImage = "gcr.io/kaniko-project/executor:v1.23.2-debug";

        /** Shared volume group, also used for non-root builder images. */
        private Long fsGroup = 1000L;

        /**
         * 工作空间存储配置。
         */
        private WorkspaceStorageConfig workspaceStorage = new WorkspaceStorageConfig();

        /**
         * 资源配额。
         */
        private ResourceQuotaConfig resourceQuota = new ResourceQuotaConfig();

        /**
         * Pod 自动清理配置。
         */
        private PodCleanupConfig podCleanup = new PodCleanupConfig();

    }

    /**
     * 工作空间存储配置。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkspaceStorageConfig {

        /**
         * 存储类型：SHARED_PVC, DYNAMIC_PVC, EMPTY_DIR
         */
        private String type = "SHARED_PVC";

        /**
         * 共享 PVC 名称（type=SHARED_PVC 时使用）。
         */
        private String sharedPvcName = "pipeline-shared-workspace";

        /**
         * 动态 PVC 存储类名称（type=DYNAMIC_PVC 时使用）。
         */
        private String storageClassName = "fast-ssd";

        /**
         * PVC 容量请求。
         */
        private String storageSize = "10Gi";

        /** Dynamic workspaces and caches may be mounted by jobs on different nodes. */
        private String accessMode = "ReadWriteMany";

        /** Existing cache PVC; SHARED_PVC may reuse sharedPvcName, other modes require this field. */
        private String cachePvcName;

    }

    /**
     * 资源配额配置。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResourceQuotaConfig {

        /**
         * CPU 请求。
         */
        private String cpuRequest = "1000m";

        /**
         * 内存请求。
         */
        private String memoryRequest = "2Gi";

        /**
         * CPU 限制。
         */
        private String cpuLimit = "2000m";

        /**
         * 内存限制。
         */
        private String memoryLimit = "4Gi";

    }

    /**
     * Pod 清理配置。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PodCleanupConfig {

        /**
         * 清理策略：IMMEDIATE（任务完成立即删除），RETAIN（保留用于调试，定时清理）
         */
        private String strategy = "IMMEDIATE";

        /**
         * 保留时长（分钟），仅在 strategy=RETAIN 时生效。
         * 用于保留失败的 Pod 以便调试，默认保留 30 分钟。
         */
        private Integer retentionMinutes = 30;

        /**
         * 是否保留失败的 Pod 用于调试。
         */
        private Boolean retainFailedPods = true;

    }

}
