package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;

import java.nio.file.Path;
import java.util.Map;

/**
 * 流水线源码工作目录准备器。
 */
public interface PipelineSourceWorkspacePreparer {

    /**
     * 根据流水线源码配置准备任务工作目录。
     *
     * @param run 流水线运行记录
     * @param spec 流水线 YAML 配置
     * @param workspace 任务工作目录
     * @param sharedState 流水线运行共享状态
     */
    void prepare(PipelineRunDO run, PipelineSpec spec, Path workspace, Map<String, Object> sharedState);

}
