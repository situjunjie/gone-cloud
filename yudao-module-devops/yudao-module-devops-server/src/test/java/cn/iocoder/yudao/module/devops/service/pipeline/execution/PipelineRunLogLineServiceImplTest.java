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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_RUN_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    public void testAppendLine_success() {
        PipelineRunLogDO runLog = buildRunLog();
        when(pipelineRunLogLineMapper.selectMaxLineNoByRunLogId(eq(900L))).thenReturn(7L);

        PipelineRunLogLineRespVO respVO = service.appendLine(runLog, "stderr", "hello");

        assertEquals(8L, respVO.getLineNo());
        assertEquals("stderr", respVO.getStreamType());
        assertEquals("hello", respVO.getContent());
        ArgumentCaptor<PipelineRunLogLineDO> captor = ArgumentCaptor.forClass(PipelineRunLogLineDO.class);
        verify(pipelineRunLogLineMapper).insert(captor.capture());
        assertEquals(800L, captor.getValue().getPipelineRunId());
        assertEquals("step-1", captor.getValue().getStepId());
    }

    @Test
    public void testAppendLine_truncated() {
        PipelineRunLogDO runLog = buildRunLog();
        when(pipelineRunLogLineMapper.selectMaxLineNoByRunLogId(eq(900L))).thenReturn(2000L);

        PipelineRunLogLineRespVO respVO = service.appendLine(runLog, "stdout", "overflow");

        assertNull(respVO);
        ArgumentCaptor<PipelineRunLogDO> captor = ArgumentCaptor.forClass(PipelineRunLogDO.class);
        verify(pipelineRunLogMapper).updateById(captor.capture());
        assertEquals(Boolean.TRUE, captor.getValue().getLogTruncated());
    }

    @Test
    public void testGetLogLines_success() {
        PipelineRunDO run = buildRun(PipelineRunStatusEnum.RUNNING.getStatus());
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(run);
        PipelineRunLogLineDO line = buildLine(11L, 1L, "step-1", "hello");
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

        assertServiceException(() -> service.getLogLines(800L, null, null, null), PIPELINE_RUN_NOT_EXISTS);
    }

    @Test
    public void testStreamLogLines_createEmitter() {
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(buildRun(PipelineRunStatusEnum.RUNNING.getStatus()));
        PipelineRunLogDO runLog = buildRunLog();
        runLog.setStatus(PipelineRunLogStatusEnum.SUCCESS.getStatus());
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("step-1"))).thenReturn(runLog);
        ReflectionTestUtils.setField(service, "pipelineLogStreamExecutor", (Executor) Runnable::run);
        when(pipelineRunLogLineMapper.selectListByCursor(eq(800L), eq("step-1"), eq(0L), eq(200)))
                .thenReturn(List.of());

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

    private PipelineRunLogLineDO buildLine(Long id, Long lineNo, String stepId, String content) {
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

}
