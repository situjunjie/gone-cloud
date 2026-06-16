package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;

import java.nio.file.Path;

/**
 * 流水线任务工作目录服务。
 */
public interface PipelineWorkspaceService {

    /**
     * 为任务创建隔离工作目录。
     *
     * @param run 流水线运行记录
     * @param job 可执行任务定义
     * @return 已创建的工作目录
     */
    Path createWorkspace(PipelineRunDO run, PipelineSpec.ExecutableJob job);

}
