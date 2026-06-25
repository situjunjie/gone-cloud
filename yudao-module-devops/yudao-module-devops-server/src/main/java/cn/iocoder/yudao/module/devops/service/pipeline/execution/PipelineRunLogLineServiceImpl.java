package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogLineDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogLineMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
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
    private static final AtomicLong TRANSIENT_CURSOR = new AtomicLong(System.currentTimeMillis());

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
        PipelineRunLogLineRespVO line = buildTransientLine(runLog, streamType, content);
        pipelineLogBroadcaster.publish(line);
        return line;
    }

    @Override
    public List<PipelineRunLogLineRespVO> getLogLines(Long pipelineRunId, String stepId, Long afterId, Integer limit) {
        validateRunExists(pipelineRunId);
        int queryLimit = limit == null || limit <= 0 ? DEFAULT_QUERY_LIMIT : Math.min(limit, 500);
        if (pipelineLogStorageService.hasRunLogFile(pipelineRunId)) {
            return pipelineLogStorageService.readLines(pipelineRunId, blankToNull(stepId), afterId, queryLimit);
        }
        return pipelineRunLogLineMapper.selectListByCursor(pipelineRunId, blankToNull(stepId), afterId, queryLimit)
                .stream().map(this::convert).toList();
    }

    @Override
    public SseEmitter streamLogLines(Long pipelineRunId, String stepId, Long afterId) {
        validateRunExists(pipelineRunId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        pipelineLogStreamExecutor.execute(() -> doStream(emitter, pipelineRunId, blankToNull(stepId),
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
                if (stepId != null && isTerminalStep(pipelineRunId, stepId)) {
                    send(emitter, "complete", stepId);
                    emitter.complete();
                    return;
                }
                if (isTerminalRun(run.getRunStatus())) {
                    send(emitter, "complete", run.getRunStatus());
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

    private PipelineRunLogLineRespVO buildTransientLine(PipelineRunLogDO runLog, String streamType, String content) {
        if (runLog == null || runLog.getPipelineRunId() == null || StrUtil.isBlank(content)) {
            return null;
        }
        PipelineRunLogLineRespVO respVO = new PipelineRunLogLineRespVO();
        respVO.setId(TRANSIENT_CURSOR.incrementAndGet());
        respVO.setPipelineRunId(runLog.getPipelineRunId());
        respVO.setRunLogId(runLog.getId());
        respVO.setStageId(runLog.getStageId());
        respVO.setJobId(runLog.getJobId());
        respVO.setStepId(runLog.getStepId());
        respVO.setStreamType("stderr".equalsIgnoreCase(streamType) ? "stderr" : "stdout");
        respVO.setContent(content);
        respVO.setCreateTime(LocalDateTime.now());
        return respVO;
    }

    private boolean isTerminalRun(Integer runStatus) {
        return Objects.equals(runStatus, PipelineRunStatusEnum.SUCCESS.getStatus())
                || Objects.equals(runStatus, PipelineRunStatusEnum.FAILED.getStatus())
                || Objects.equals(runStatus, PipelineRunStatusEnum.CANCELED.getStatus());
    }

    private boolean isTerminalStep(Long pipelineRunId, String stepId) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(pipelineRunId, stepId);
        return log != null && (PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(log.getStatus())
                || PipelineRunLogStatusEnum.FAILED.getStatus().equals(log.getStatus())
                || PipelineRunLogStatusEnum.CANCELED.getStatus().equals(log.getStatus()));
    }

    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value;
    }

}
