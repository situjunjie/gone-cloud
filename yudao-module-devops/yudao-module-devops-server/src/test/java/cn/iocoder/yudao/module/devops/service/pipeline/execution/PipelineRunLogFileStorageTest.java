package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PipelineRunLogFileStorage} 的单元测试。
 */
public class PipelineRunLogFileStorageTest extends BaseMockitoUnitTest {

    private final PipelineRunLogFileStorage storage = new PipelineRunLogFileStorage();

    @TempDir
    private Path tempDir;

    @Test
    public void testPrepareAndReadLines() throws Exception {
        PipelineRunLogDO runLog = buildRunLog();
        runLog.setWorkspacePath(tempDir.toString());

        PipelineRunLogFileStorage.PipelineRunLogFiles files = storage.prepare(runLog, tempDir);
        Files.writeString(files.getStdoutPath(), "hello\nworld\n", StandardCharsets.UTF_8);
        Files.writeString(files.getStderrPath(), "warn\n", StandardCharsets.UTF_8);

        List<PipelineRunLogLineRespVO> result = storage.readLines(List.of(runLog), "step-1", 1L, 10);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).getId());
        assertEquals(2L, result.get(0).getLineNo());
        assertEquals("stdout", result.get(0).getStreamType());
        assertEquals("world", result.get(0).getContent());
        assertEquals(3L, result.get(1).getId());
        assertEquals("stderr", result.get(1).getStreamType());
        assertEquals("warn", result.get(1).getContent());
    }

    @Test
    public void testAppendLineAndBuildFullLog() {
        PipelineRunLogDO runLog = buildRunLog();
        runLog.setWorkspacePath(tempDir.toString());

        storage.appendLine(runLog, "stdout", "hello");
        storage.appendLine(runLog, "stderr", "warn");

        assertTrue(storage.hasAnyLogFile(List.of(runLog)));
        byte[] result = storage.buildFullLog(runLog);

        assertArrayEquals(("[stdout] hello" + System.lineSeparator()
                + "[stderr] warn" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8), result);
    }

    private PipelineRunLogDO buildRunLog() {
        PipelineRunLogDO runLog = new PipelineRunLogDO();
        runLog.setId(900L);
        runLog.setPipelineRunId(800L);
        runLog.setStageId("stage-1");
        runLog.setJobId("job-1");
        runLog.setStepId("step-1");
        return runLog;
    }

}
