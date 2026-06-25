package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import org.springframework.core.io.Resource;

import java.nio.file.Path;
import java.util.List;

/**
 * 流水线文件日志存储服务。
 */
public interface PipelineLogStorageService {

    /**
     * 准备步骤原始日志文件。
     *
     * @param runLog 运行日志
     * @return 主机侧日志文件路径
     */
    Path prepareStepLog(PipelineRunLogDO runLog);

    /**
     * 获取步骤原始日志文件路径，不创建文件。
     *
     * @param runLog 运行日志
     * @return 主机侧日志文件路径
     */
    Path getStepLogFile(PipelineRunLogDO runLog);

    /**
     * 获取容器内可访问的步骤日志文件路径。
     *
     * @param runLog 运行日志
     * @return 容器内日志文件路径
     */
    String getContainerStepLogFile(PipelineRunLogDO runLog);

    /**
     * 按 cursor 读取日志行。
     *
     * @param pipelineRunId 流水线运行编号
     * @param stepId 步骤编号，可空
     * @param afterId cursor，可空
     * @param limit 读取条数
     * @return 日志行
     */
    List<PipelineRunLogLineRespVO> readLines(Long pipelineRunId, String stepId, Long afterId, Integer limit);

    /**
     * 判断运行是否存在文件日志。
     *
     * @param pipelineRunId 流水线运行编号
     * @return 是否存在
     */
    boolean hasRunLogFile(Long pipelineRunId);

    /**
     * 完成步骤日志写入，并尝试归档。
     *
     * @param runLog 运行日志
     */
    void completeStepLog(PipelineRunLogDO runLog);

    /**
     * 获取步骤原始日志文件资源。
     *
     * @param pipelineRunId 流水线运行编号
     * @param runLogId 运行日志编号
     * @return 文件资源
     */
    Resource getRawLogResource(Long pipelineRunId, Long runLogId);

}
