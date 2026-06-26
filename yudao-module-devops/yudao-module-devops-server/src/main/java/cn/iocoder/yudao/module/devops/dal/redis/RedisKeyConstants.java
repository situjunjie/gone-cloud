package cn.iocoder.yudao.module.devops.dal.redis;

import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseCurrentRunRespVO;

/**
 * DevOps Redis Key 枚举类。
 */
public interface RedisKeyConstants {

    /**
     * 应用发布当前流水线运行的缓存，使用 Spring Cache 操作。
     * <p>
     * KEY 格式：devops:application_release_current_run:${applicationEnvId}
     * VALUE 数据类型：String(JSON)，即 {@link ApplicationReleaseCurrentRunRespVO}
     */
    String APPLICATION_RELEASE_CURRENT_RUN = "devops:application_release_current_run#5s";

    /**
     * 流水线工作目录操作的分布式锁。
     * <p>
     * KEY 格式：devops:pipeline_workspace_lock:${workspacePath}
     * VALUE 数据类型：HASH，即 Redisson RLock 内部结构。
     * 过期时间：无固定 TTL，依赖 tryLock 的 lease 兜底自动释放，避免节点宕机导致死锁，
     * 具体时长见 {@link cn.iocoder.yudao.module.devops.framework.pipeline.runtime.LocalPipelineWorkspaceService}。
     * <p>
     * 假设：多节点共享存储时各节点挂载到相同路径（与原进程锁用 workspacePath 作 key 的隐含假设一致）。
     */
    String PIPELINE_WORKSPACE_LOCK = "devops:pipeline_workspace_lock:%s";

}
