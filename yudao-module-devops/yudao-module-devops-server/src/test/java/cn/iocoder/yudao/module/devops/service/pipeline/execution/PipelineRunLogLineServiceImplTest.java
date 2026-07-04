package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogLineDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogLineMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_RUN_LOG_STEP_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_RUN_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PipelineRunLogLineServiceImpl} 的单元测试。
 */
public class PipelineRunLogLineServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private PipelineRunLogLineServiceImpl service;

    @Mock
    private PipelineRunMapper pipelineRunMapper;
    @Mock
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Mock
    private PipelineRunLogLineMapper pipelineRunLogLineMapper;
    @Mock
    private PipelineLogStorageService pipelineLogStorageService;
    @Mock
    private PipelineLogBroadcaster pipelineLogBroadcaster;

    @Test
    public void testAppendLine_success() {
        PipelineRunLogDO runLog = buildRunLog();
        runLog.setWorkspacePath("/tmp/workspace");
        PipelineRunLogLineRespVO line = buildRespLine(21L, 1L, "step-1", "hello");
        line.setStreamType("stderr");
        when(pipelineLogStorageService.appendStepLogLine(eq(runLog), eq("stderr"), eq("hello"))).thenReturn(line);

        PipelineRunLogLineRespVO respVO = service.appendLine(runLog, "stderr", "hello");

        assertEquals("stderr", respVO.getStreamType());
        assertEquals("hello", respVO.getContent());
        verify(pipelineLogBroadcaster).publish(respVO);
    }

    @Test
    public void testAppendLine_noWorkspaceDbFallback() {
        PipelineRunLogDO runLog = buildRunLog();
        when(pipelineRunLogLineMapper.selectMaxLineNoByRunLogId(eq(900L))).thenReturn(2L);
        when(pipelineRunLogLineMapper.insert(any(PipelineRunLogLineDO.class))).thenAnswer(invocation -> {
            PipelineRunLogLineDO line = invocation.getArgument(0);
            line.setId(23L);
            return 1;
        });

        PipelineRunLogLineRespVO respVO = service.appendLine(runLog, "stdout", "platform log");

        assertEquals(23L, respVO.getId());
        assertEquals(3L, respVO.getLineNo());
        assertEquals("platform log", respVO.getContent());
        verify(pipelineRunLogLineMapper).insert(any(PipelineRunLogLineDO.class));
        verify(pipelineLogBroadcaster).publish(respVO);
    }

    @Test
    public void testAppendLine_blankContent() {
        PipelineRunLogDO runLog = buildRunLog();
        runLog.setWorkspacePath("/tmp/workspace");
        when(pipelineLogStorageService.appendStepLogLine(eq(runLog), eq("stdout"), eq(""))).thenReturn(null);

        PipelineRunLogLineRespVO respVO = service.appendLine(runLog, "stdout", "");

        assertNull(respVO);
    }

    @Test
    public void testGetLogLines_fileBacked() {
        PipelineRunDO run = buildRun(PipelineRunStatusEnum.RUNNING.getStatus());
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(run);
        PipelineRunLogLineRespVO line = buildRespLine(21L, 1L, "step-1", "hello");
        when(pipelineLogStorageService.hasStepLogFile(eq(800L), eq("step-1"))).thenReturn(true);
        when(pipelineLogStorageService.readLines(eq(800L), eq("step-1"), eq(10L), eq(100)))
                .thenReturn(List.of(line));

        List<PipelineRunLogLineRespVO> result = service.getLogLines(800L, "step-1", 10L, 100);

        assertEquals(1, result.size());
        assertEquals(21L, result.get(0).getId());
        assertEquals(1L, result.get(0).getLineNo());
    }

    @Test
    public void testGetLogLines_legacyDbFallback() {
        PipelineRunDO run = buildRun(PipelineRunStatusEnum.RUNNING.getStatus());
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(run);
        when(pipelineLogStorageService.hasStepLogFile(eq(800L), eq("step-1"))).thenReturn(false);
        PipelineRunLogLineDO line = buildDbLine(11L, 1L, "step-1", "hello");
        when(pipelineRunLogLineMapper.selectListByCursor(eq(800L), eq("step-1"), eq(10L), eq(100)))
                .thenReturn(List.of(line));

        List<PipelineRunLogLineRespVO> result = service.getLogLines(800L, "step-1", 10L, 100);

        assertEquals(1, result.size());
        assertEquals(11L, result.get(0).getId());
        assertEquals(1L, result.get(0).getLineNo());
    }

    @Test
    public void testGetLogLines_runNotExists() {
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(null);

        assertServiceException(() -> service.getLogLines(800L, "step-1", null, null), PIPELINE_RUN_NOT_EXISTS);
    }

    @Test
    public void testGetLogLines_stepRequired() {
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(buildRun(PipelineRunStatusEnum.RUNNING.getStatus()));

        assertServiceException(() -> service.getLogLines(800L, null, null, null), PIPELINE_RUN_LOG_STEP_REQUIRED);
    }

    @Test
    public void testStreamLogLines_createEmitter() {
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(buildRun(PipelineRunStatusEnum.RUNNING.getStatus()));
        PipelineRunLogDO runLog = buildRunLog();
        runLog.setStatus(PipelineRunLogStatusEnum.SUCCESS.getStatus());
        ReflectionTestUtils.setField(service, "pipelineLogStreamExecutor", (Executor) Runnable::run);
        PipelineLogBroadcaster realBroadcaster = new PipelineLogBroadcaster();
        when(pipelineLogBroadcaster.subscribe(eq(800L), eq("step-1"), eq(0L), any(SseEmitter.class)))
                .thenAnswer(invocation -> realBroadcaster.subscribe(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2), invocation.getArgument(3)));
        when(pipelineLogStorageService.hasStepLogFile(eq(800L), eq("step-1"))).thenReturn(true);
        when(pipelineLogStorageService.readLines(eq(800L), eq("step-1"), eq(0L), eq(200)))
                .thenReturn(List.of());
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("step-1"))).thenReturn(runLog);

        SseEmitter emitter = service.streamLogLines(800L, "step-1", 0L);

        assertNotNull(emitter);
    }

    private PipelineRunLogDO buildRunLog() {
        PipelineRunLogDO runLog = new PipelineRunLogDO();
        runLog.setId(900L);
        runLog.setPipelineRunId(800L);
        runLog.setTenantId(1L);
        runLog.setStageId("stage-1");
        runLog.setJobId("job-1");
        runLog.setStepId("step-1");
        return runLog;
    }

    private PipelineRunDO buildRun(Integer status) {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setRunStatus(status);
        return run;
    }

    private PipelineRunLogLineDO buildDbLine(Long id, Long lineNo, String stepId, String content) {
        PipelineRunLogLineDO line = new PipelineRunLogLineDO();
        line.setId(id);
        line.setPipelineRunId(800L);
        line.setRunLogId(900L);
        line.setStepId(stepId);
        line.setLineNo(lineNo);
        line.setStreamType("stdout");
        line.setContent(content);
        return line;
    }

    private PipelineRunLogLineRespVO buildRespLine(Long id, Long lineNo, String stepId, String content) {
        PipelineRunLogLineRespVO line = new PipelineRunLogLineRespVO();
        line.setId(id);
        line.setPipelineRunId(800L);
        line.setRunLogId(900L);
        line.setStepId(stepId);
        line.setLineNo(lineNo);
        line.setStreamType("stdout");
        line.setContent(content);
        return line;
    }

}
