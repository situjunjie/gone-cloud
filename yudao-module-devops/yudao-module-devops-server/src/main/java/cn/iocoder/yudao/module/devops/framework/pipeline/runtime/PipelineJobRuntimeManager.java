package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;

import java.nio.file.Path;

/**
 * 流水线任务运行时管理器。
 */
public interface PipelineJobRuntimeManager {

    /**
     * 为流水线任务创建隔离运行时。
     *
     * @param run 流水线运行记录
     * @param job 可执行任务定义
     * @param workspace 已准备好的任务工作目录
     * @return 任务运行时
     */
    PipelineJobRuntime createRuntime(PipelineRunDO run, PipelineSpec.ExecutableJob job, Path workspace);

    /**
     * 销毁流水线任务运行时。
     *
     * @param runtime 任务运行时
     */
    void destroyRuntime(PipelineJobRuntime runtime);

}
