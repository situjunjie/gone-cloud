package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PipelineLogStorageServiceImpl} 的单元测试。
 */
public class PipelineLogStorageServiceImplTest extends BaseMockitoUnitTest {

    @TempDir
    private Path tempDir;

    @InjectMocks
    private PipelineLogStorageServiceImpl service;

    @Mock
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Mock
    private FileApi fileApi;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(service, "logRoot", tempDir.toString());
        ReflectionTestUtils.setField(service, "archiveEnabled", true);
        ReflectionTestUtils.setField(service, "archiveDirectory", "devops/pipeline-logs");
        ReflectionTestUtils.setField(service, "archiveMaxBytes", 1024L * 1024L);
        ReflectionTestUtils.setField(service, "maxRunBytes", 1024L * 1024L);
        ReflectionTestUtils.setField(service, "maxLineBytes", 16);
    }

    @Test
    public void testPrepareAndReadLines_byCursor() throws Exception {
        PipelineRunLogDO runLog = buildRunLog(900L, "step-1");
        when(pipelineRunLogMapper.selectListByPipelineRunId(eq(800L))).thenReturn(List.of(runLog));

        Path rawLog = service.prepareStepLog(runLog);
        Files.writeString(rawLog, "hello\nworld\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        List<PipelineRunLogLineRespVO> firstBatch = service.readLines(800L, null, 0L, 10);
        List<PipelineRunLogLineRespVO> lines = service.readLines(800L, null, firstBatch.get(0).getId(), 10);

        assertEquals(1, lines.size());
        assertEquals(2L, lines.get(0).getLineNo());
        assertEquals("world", lines.get(0).getContent());
        assertEquals("hello\nworld\n", Files.readString(rawLog, StandardCharsets.UTF_8));
    }

    @Test
    public void testReadLines_stepFilter() throws Exception {
        PipelineRunLogDO secondLog = buildRunLog(901L, "step-2");
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("step-2"))).thenReturn(secondLog);
        Path rawLog = service.prepareStepLog(secondLog);
        Files.writeString(rawLog, "world\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        List<PipelineRunLogLineRespVO> lines = service.readLines(800L, "step-2", 0L, 10);

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).getId() > 0);
        assertEquals("step-2", lines.get(0).getStepId());
    }

    @Test
    public void testReadLines_maxLineBytesTruncatesUtf8Safely() throws Exception {
        ReflectionTestUtils.setField(service, "maxLineBytes", 24);
        PipelineRunLogDO runLog = buildRunLog(900L, "step-1");
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("step-1"))).thenReturn(runLog);
        Path rawLog = service.prepareStepLog(runLog);
        Files.writeString(rawLog, "中文中文中文中文中\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        PipelineRunLogLineRespVO line = service.readLines(800L, "step-1", 0L, 10).get(0);

        assertTrue(line.getContent().endsWith("... [line truncated]"));
        assertTrue(line.getContent().getBytes(StandardCharsets.UTF_8).length <= 24);
    }

    @Test
    public void testCompleteStepLog_maxRunBytesMarksTruncated() throws Exception {
        ReflectionTestUtils.setField(service, "maxRunBytes", 1L);
        PipelineRunLogDO runLog = buildRunLog(900L, "step-1");
        Path rawLog = service.prepareStepLog(runLog);
        Files.writeString(rawLog, "hello\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        service.completeStepLog(runLog);

        ArgumentCaptor<PipelineRunLogDO> captor = ArgumentCaptor.forClass(PipelineRunLogDO.class);
        verify(pipelineRunLogMapper, atLeastOnce()).updateById(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(log -> Boolean.TRUE.equals(log.getLogTruncated())));
    }

    @Test
    public void testCompleteStepLog_archiveSuccess() throws Exception {
        PipelineRunLogDO runLog = buildRunLog(900L, "step-1");
        Path rawLog = service.prepareStepLog(runLog);
        Files.writeString(rawLog, "hello\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        when(fileApi.createFile(any(byte[].class), eq("pipeline-run-800-log-900.log"),
                eq("devops/pipeline-logs"), eq("text/plain"))).thenReturn("https://example.com/log");

        service.completeStepLog(runLog);

        ArgumentCaptor<PipelineRunLogDO> captor = ArgumentCaptor.forClass(PipelineRunLogDO.class);
        verify(pipelineRunLogMapper, atLeastOnce()).updateById(captor.capture());
        PipelineRunLogDO updated = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("https://example.com/log", updated.getLogFileUrl());
        assertEquals(PipelineLogArchiveStatus.UPLOADED, updated.getLogArchiveStatus());
        assertEquals(6L, updated.getLogSizeBytes());
        assertEquals(1L, updated.getLogLineCount());
        assertEquals("tenants/tenant-1/runs/run-800/steps/run-log-900-step-1.log",
                updated.getLogFilePath().replace('\\', '/'));
        assertNotNull(updated.getLogArchivedAt());
    }

    @Test
    public void testCompleteStepLog_archiveDisabled() throws Exception {
        ReflectionTestUtils.setField(service, "archiveEnabled", false);
        PipelineRunLogDO runLog = buildRunLog(900L, "step-1");
        Path rawLog = service.prepareStepLog(runLog);
        Files.writeString(rawLog, "hello\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        service.completeStepLog(runLog);

        ArgumentCaptor<PipelineRunLogDO> captor = ArgumentCaptor.forClass(PipelineRunLogDO.class);
        verify(pipelineRunLogMapper, atLeastOnce()).updateById(captor.capture());
        PipelineRunLogDO updated = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(PipelineLogArchiveStatus.LOCAL_ONLY, updated.getLogArchiveStatus());
        verify(fileApi, never()).createFile(any(byte[].class), any(), any(), any());
    }

    @Test
    public void testGetRawLogResource_success() throws Exception {
        PipelineRunLogDO runLog = buildRunLog(900L, "step-1");
        when(pipelineRunLogMapper.selectById(eq(900L))).thenReturn(runLog);
        Path rawLog = service.prepareStepLog(runLog);
        Files.writeString(rawLog, "hello\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        Resource resource = service.getRawLogResource(800L, 900L);

        assertTrue(resource.exists());
        assertEquals("hello\n", Files.readString(resource.getFile().toPath(), StandardCharsets.UTF_8));
    }

    private PipelineRunLogDO buildRunLog(Long id, String stepId) {
        PipelineRunLogDO runLog = new PipelineRunLogDO();
        runLog.setId(id);
        runLog.setPipelineRunId(800L);
        runLog.setTenantId(1L);
        runLog.setStageId("stage-1");
        runLog.setJobId("job-1");
        runLog.setStepId(stepId);
        return runLog;
    }

}
