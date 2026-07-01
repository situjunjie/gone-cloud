package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogStreamCompleteRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogLineDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogLineMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_RUN_LOG_STEP_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_RUN_NOT_EXISTS;

/**
 * 流水线行级运行日志 Service 实现。
 */
@Slf4j
@Service
@Validated
public class PipelineRunLogLineServiceImpl implements PipelineRunLogLineService {

    private static final int DEFAULT_QUERY_LIMIT = 200;
    private static final long SSE_TIMEOUT_MILLIS = 0L;
    private static final long POLL_INTERVAL_MILLIS = 1000L;

    @Resource
    private PipelineRunMapper pipelineRunMapper;
    @Resource
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Resource
    private PipelineRunLogLineMapper pipelineRunLogLineMapper;
    @Resource
    @Qualifier("pipelineLogStreamExecutor")
    private Executor pipelineLogStreamExecutor;
    @Resource
    private PipelineLogStorageService pipelineLogStorageService;
    @Resource
    private PipelineLogBroadcaster pipelineLogBroadcaster;

    @Override
    public PipelineRunLogLineRespVO appendLine(PipelineRunLogDO runLog, String streamType, String content) {
        PipelineRunLogLineRespVO line = pipelineLogStorageService.appendStepLogLine(runLog, streamType, content);
        if (line != null) {
            pipelineLogBroadcaster.publish(line);
        }
        return line;
    }

    @Override
    public List<PipelineRunLogLineRespVO> getLogLines(Long pipelineRunId, String stepId, Long afterId, Integer limit) {
        validateRunExists(pipelineRunId);
        validateStepId(stepId);
        int queryLimit = limit == null || limit <= 0 ? DEFAULT_QUERY_LIMIT : Math.min(limit, 500);
        if (pipelineLogStorageService.hasStepLogFile(pipelineRunId, stepId)) {
            return pipelineLogStorageService.readLines(pipelineRunId, stepId, afterId, queryLimit);
        }
        return pipelineRunLogLineMapper.selectListByCursor(pipelineRunId, stepId, afterId, queryLimit)
                .stream().map(this::convert).toList();
    }

    @Override
    public SseEmitter streamLogLines(Long pipelineRunId, String stepId, Long afterId) {
        validateRunExists(pipelineRunId);
        validateStepId(stepId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        pipelineLogStreamExecutor.execute(() -> doStream(emitter, pipelineRunId, stepId,
                afterId == null ? 0L : afterId));
        return emitter;
    }

    @Override
    public org.springframework.core.io.Resource getRawLogResource(Long pipelineRunId, Long runLogId) {
        validateRunExists(pipelineRunId);
        return pipelineLogStorageService.getRawLogResource(pipelineRunId, runLogId);
    }

    private void doStream(SseEmitter emitter, Long pipelineRunId, String stepId, Long cursor) {
        PipelineLogBroadcaster.Subscription subscription = pipelineLogBroadcaster.subscribe(pipelineRunId, stepId, cursor, emitter);
        try {
            while (true) {
                List<PipelineRunLogLineRespVO> lines = getLogLines(pipelineRunId, stepId, cursor, DEFAULT_QUERY_LIMIT);
                for (PipelineRunLogLineRespVO line : lines) {
                    subscription.send(line);
                    cursor = line.getId();
                }
                PipelineRunDO run = pipelineRunMapper.selectById(pipelineRunId);
                if (run == null) {
                    emitter.completeWithError(exception(PIPELINE_RUN_NOT_EXISTS));
                    return;
                }
                if (!lines.isEmpty()) {
                    continue;
                }
                if (subscription.isClosed()) {
                    return;
                }
                if (isTerminalStep(pipelineRunId, stepId)) {
                    send(emitter, "complete", buildCompletePayload(pipelineRunId, stepId));
                    emitter.complete();
                    return;
                }
                Thread.sleep(POLL_INTERVAL_MILLIS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (Throwable ex) {
            log.warn("[doStream][runId({}) log stream failed: {}]", pipelineRunId, ex.getMessage(), ex);
            emitter.completeWithError(ex);
        } finally {
            pipelineLogBroadcaster.unsubscribe(subscription);
        }
    }

    private void send(SseEmitter emitter, String event, Object data) throws IOException {
        if (data instanceof PipelineRunLogLineRespVO line) {
            emitter.send(SseEmitter.event().name(event).id(String.valueOf(line.getId())).data(line));
            return;
        }
        emitter.send(SseEmitter.event().name(event).data(data));
    }

    private void validateRunExists(Long pipelineRunId) {
        if (pipelineRunMapper.selectById(pipelineRunId) == null) {
            throw exception(PIPELINE_RUN_NOT_EXISTS);
        }
    }

    private void validateStepId(String stepId) {
        if (StrUtil.isBlank(stepId)) {
            throw exception(PIPELINE_RUN_LOG_STEP_REQUIRED);
        }
    }

    private PipelineRunLogLineRespVO convert(PipelineRunLogLineDO logLine) {
        PipelineRunLogLineRespVO respVO = new PipelineRunLogLineRespVO();
        respVO.setId(logLine.getId());
        respVO.setPipelineRunId(logLine.getPipelineRunId());
        respVO.setRunLogId(logLine.getRunLogId());
        respVO.setStageId(logLine.getStageId());
        respVO.setJobId(logLine.getJobId());
        respVO.setStepId(logLine.getStepId());
        respVO.setLineNo(logLine.getLineNo());
        respVO.setStreamType(logLine.getStreamType());
        respVO.setContent(logLine.getContent());
        respVO.setCreateTime(logLine.getCreateTime());
        return respVO;
    }

    private boolean isTerminalStep(Long pipelineRunId, String stepId) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(pipelineRunId, stepId);
        return log != null && (PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(log.getStatus())
                || PipelineRunLogStatusEnum.FAILED.getStatus().equals(log.getStatus())
                || PipelineRunLogStatusEnum.CANCELED.getStatus().equals(log.getStatus()));
    }

    private PipelineRunLogStreamCompleteRespVO buildCompletePayload(Long pipelineRunId, String stepId) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(pipelineRunId, stepId);
        if (log != null) {
            pipelineLogStorageService.archiveStepLog(log);
            log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(pipelineRunId, stepId);
        }
        PipelineRunLogStreamCompleteRespVO respVO = new PipelineRunLogStreamCompleteRespVO();
        respVO.setPipelineRunId(pipelineRunId);
        respVO.setStepId(stepId);
        respVO.setTerminal(true);
        if (log == null) {
            return respVO;
        }
        respVO.setRunLogId(log.getId());
        respVO.setStageId(log.getStageId());
        respVO.setJobId(log.getJobId());
        respVO.setStatus(log.getStatus());
        respVO.setLogFileUrl(log.getLogFileUrl());
        respVO.setLogArchiveStatus(log.getLogArchiveStatus());
        return respVO;
    }

}
