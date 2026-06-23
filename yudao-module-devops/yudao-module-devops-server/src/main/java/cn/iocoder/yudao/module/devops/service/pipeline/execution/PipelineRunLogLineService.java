package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 流水线行级运行日志 Service。
 */
public interface PipelineRunLogLineService {

    /**
     * 追加一行步骤输出日志。
     *
     * @param runLog 步骤运行日志
     * @param streamType 输出流类型，stdout / stderr
     * @param content 日志内容
     * @return 追加后的日志行；超过上限时返回 null
     */
    PipelineRunLogLineRespVO appendLine(PipelineRunLogDO runLog, String streamType, String content);

    /**
     * 查询指定游标后的日志行。
     *
     * @param pipelineRunId 流水线运行编号
     * @param stepId 步骤编号，可空
     * @param afterId 游标编号，仅返回该游标后的行
     * @param limit 返回条数
     * @return 日志行列表
     */
    List<PipelineRunLogLineRespVO> getLogLines(Long pipelineRunId, String stepId, Long afterId, Integer limit);

    /**
     * 创建日志 SSE 流。
     *
     * @param pipelineRunId 流水线运行编号
     * @param stepId 步骤编号，可空
     * @param afterId 游标编号，仅推送该游标后的行
     * @return SSE emitter
     */
    SseEmitter streamLogLines(Long pipelineRunId, String stepId, Long afterId);

}
