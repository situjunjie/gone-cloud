package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 流水线执行异步启动服务。
 *
 * <p>提交按钮触发后，异步启动当前应用环境已发布的流水线 YAML。
 */
@Service
@Slf4j
public class PipelineExecutionAsyncService {

    @Resource
    private PipelineRunMapper pipelineRunMapper;
    @Resource
    private PipelineExecutionEngine pipelineExecutionEngine;

    /**
     * 异步开启流水线执行。
     *
     * @param pipelineRunId 流水线运行编号
     * @param changeIds     提交时的变更编号快照
     * @param userId        触发用户编号
     */
    @Async
    public void startPipelineAsync(Long pipelineRunId, List<Long> changeIds, Long userId) {
        try {
            PipelineRunDO run = pipelineRunMapper.selectById(pipelineRunId);
            if (run == null) {
                log.warn("[startPipelineAsync][pipelineRunId({}) 不存在,跳过]", pipelineRunId);
                return;
            }
            pipelineExecutionEngine.execute(run, userId);
        } catch (Exception ex) {
            log.error("[startPipelineAsync][pipelineRunId({}) changeIds({}) userId({}) 启动流水线执行失败]",
                    pipelineRunId, changeIds, userId, ex);
        }
    }

}
