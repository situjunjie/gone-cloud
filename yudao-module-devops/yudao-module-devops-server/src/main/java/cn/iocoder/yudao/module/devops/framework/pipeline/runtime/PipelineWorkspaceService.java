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
     * @param cacheConfig 缓存目录配置；为空时使用默认配置
     * @return 已创建的工作目录描述
     */
    PipelineWorkspace createWorkspace(PipelineRunDO run, PipelineCacheConfig cacheConfig);

    /**
     * 为一次流水线运行创建或返回共享工作目录，并准备任务私有子目录与启用的缓存挂载目录。
     *
     * @param run 流水线运行记录
     * @param job 可执行任务定义
     * @param cacheConfig 缓存目录配置；为空时使用默认配置
     * @return 已创建的工作目录描述
     */
    PipelineWorkspace createWorkspace(PipelineRunDO run, PipelineSpec.ExecutableJob job, PipelineCacheConfig cacheConfig);

    /**
     * 重置一次流水线运行的 run workspace:删除目录下的全部内容,只保留(并复用)目录本身。
     *
     * <p>用于每次构建开始时清空上一次构建残留的源码与中间产物,随后由源码准备器重新 {@code git} 拉取,
     * 得到全新工作区。依赖/构建缓存目录是 run workspace 的兄弟目录(definition 级常驻 cache),
     * 不在清理范围内,因此跨运行缓存不受影响。
     *
     * @param run 流水线运行记录
     */
    void resetRunWorkspace(PipelineRunDO run);

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
     * @param cachePaths 缓存目录规范路径集合
     */
    void clearDefinitionCache(PipelineDefinitionDO definition, Collection<String> cachePaths);

    /**
     * 获取已记录的缓存目录路径。
     *
     * @param definition 流水线定义
     * @return 容器缓存目录路径集合
     */
    Set<String> listRecordedCachePaths(PipelineDefinitionDO definition);

}
