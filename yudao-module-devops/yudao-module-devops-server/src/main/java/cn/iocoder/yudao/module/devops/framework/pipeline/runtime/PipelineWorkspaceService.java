package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

/**
 * 流水线工作目录服务。
 */
public interface PipelineWorkspaceService {

    /**
     * 为一次流水线运行创建或返回共享工作目录，并准备启用的缓存挂载目录。
     *
     * @param run 流水线运行记录
     * @param job 可执行任务定义
     * @param cacheConfig 缓存目录配置；为空时使用默认配置
     * @return 已创建的工作目录描述
     */
    PipelineWorkspace createWorkspace(PipelineRunDO run, PipelineSpec.ExecutableJob job, PipelineCacheConfig cacheConfig);

    /**
     * 获取流水线定义缓存根目录。
     *
     * @param definition 流水线定义
     * @return 缓存根目录
     */
    Path getDefinitionCacheRoot(PipelineDefinitionDO definition);

    /**
     * 清理流水线定义的缓存目录。
     *
     * @param definition 流水线定义
     * @param containerPaths 容器缓存目录路径集合
     */
    void clearDefinitionCache(PipelineDefinitionDO definition, Collection<String> containerPaths);

    /**
     * 获取已记录的缓存目录路径。
     *
     * @param definition 流水线定义
     * @return 容器缓存目录路径集合
     */
    Set<String> listRecordedCachePaths(PipelineDefinitionDO definition);

}
