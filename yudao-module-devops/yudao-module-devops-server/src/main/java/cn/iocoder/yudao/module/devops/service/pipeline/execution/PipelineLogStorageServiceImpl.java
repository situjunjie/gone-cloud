package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_RUN_LOG_FILE_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_RUN_LOG_NOT_EXISTS;

/**
 * 流水线文件日志存储服务实现。
 */
@Slf4j
@Service
@Validated
public class PipelineLogStorageServiceImpl implements PipelineLogStorageService {

    private static final String STEP_LOG_DIRECTORY = "steps";
    private static final String CONTAINER_LOG_ROOT = "/pipeline-logs";
    private static final String TEXT_PLAIN = "text/plain";
    private static final String TRUNCATED_MARKER = "... [line truncated]";
    private static final long CURSOR_OFFSET_FACTOR = 1_000_000_000_000L;

    @Value("${yudao.devops.pipeline.logs.root:${java.io.tmpdir}/gone-devops/pipeline-logs}")
    private String logRoot;
    @Value("${yudao.devops.pipeline.logs.archive-enabled:true}")
    private boolean archiveEnabled;
    @Value("${yudao.devops.pipeline.logs.archive-directory:devops/pipeline-logs}")
    private String archiveDirectory;
    @Value("${yudao.devops.pipeline.logs.archive-max-bytes:52428800}")
    private long archiveMaxBytes;
    @Value("${yudao.devops.pipeline.logs.max-run-bytes:209715200}")
    private long maxRunBytes;
    @Value("${yudao.devops.pipeline.logs.max-line-bytes:16384}")
    private int maxLineBytes;

    @Resource
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Resource
    private FileApi fileApi;

    @Override
    public Path prepareStepLog(PipelineRunLogDO runLog) {
        if (runLog == null || runLog.getId() == null || runLog.getPipelineRunId() == null) {
            throw new IllegalArgumentException("runLog is required");
        }
        Path rawLog = rawLogFile(runLog);
        try {
            Files.createDirectories(rawLog.getParent());
            if (!Files.exists(rawLog)) {
                Files.createFile(rawLog);
            }
            String relativePath = relativeToRoot(rawLog);
            if (!relativePath.equals(runLog.getLogFilePath())) {
                runLog.setLogFilePath(relativePath);
                runLog.setLogArchiveStatus(PipelineLogArchiveStatus.PENDING);
                pipelineRunLogMapper.updateById(runLog);
            }
            return rawLog;
        } catch (IOException ex) {
            throw new IllegalStateException("Prepare pipeline log file failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Path getStepLogFile(PipelineRunLogDO runLog) {
        return rawLogFile(runLog);
    }

    @Override
    public String getContainerStepLogFile(PipelineRunLogDO runLog) {
        return CONTAINER_LOG_ROOT + "/" + relativeToRoot(rawLogFile(runLog)).replace('\\', '/');
    }

    @Override
    public List<PipelineRunLogLineRespVO> readLines(Long pipelineRunId, String stepId, Long afterId, Integer limit) {
        int queryLimit = limit == null || limit <= 0 ? 200 : Math.min(limit, 500);
        List<PipelineRunLogLineRespVO> result = new ArrayList<>(queryLimit);
        List<PipelineRunLogDO> runLogs = findReadableRunLogs(pipelineRunId, stepId);
        for (PipelineRunLogDO runLog : runLogs) {
            if (result.size() >= queryLimit) {
                break;
            }
            readRawLines(runLog, afterId, queryLimit, result);
        }
        return result;
    }

    @Override
    public boolean hasRunLogFile(Long pipelineRunId) {
        return findReadableRunLogs(pipelineRunId, null).stream()
                .map(this::rawLogFile)
                .anyMatch(Files::isRegularFile);
    }

    @Override
    public void completeStepLog(PipelineRunLogDO runLog) {
        if (runLog == null || runLog.getId() == null || runLog.getPipelineRunId() == null) {
            return;
        }
        try {
            Path rawLog = rawLogFile(runLog);
            if (!Files.isRegularFile(rawLog)) {
                return;
            }
            long size = Files.size(rawLog);
            long lineCount = countLines(rawLog);
            runLog.setLogFilePath(relativeToRoot(rawLog));
            runLog.setLogSizeBytes(size);
            runLog.setLogLineCount(lineCount);
            if (size > maxRunBytes) {
                runLog.setLogTruncated(true);
            }
            archiveStepLog(runLog, rawLog, size);
            pipelineRunLogMapper.updateById(runLog);
        } catch (Exception ex) {
            log.warn("[completeStepLog][runLogId({}) complete log failed: {}]", runLog.getId(), ex.getMessage(), ex);
        }
    }

    @Override
    public org.springframework.core.io.Resource getRawLogResource(Long pipelineRunId, Long runLogId) {
        PipelineRunLogDO runLog = pipelineRunLogMapper.selectById(runLogId);
        if (runLog == null || !pipelineRunId.equals(runLog.getPipelineRunId())) {
            throw exception(PIPELINE_RUN_LOG_NOT_EXISTS);
        }
        Path rawLog = rawLogFile(runLog);
        if (!Files.isRegularFile(rawLog)) {
            throw exception(PIPELINE_RUN_LOG_FILE_NOT_EXISTS);
        }
        return new FileSystemResource(rawLog);
    }

    private PipelineRunLogLineRespVO convert(PipelineRunLogDO runLog, String content, long lineNo, long offset) {
        PipelineRunLogLineRespVO respVO = new PipelineRunLogLineRespVO();
        respVO.setId(encodeCursor(runLog, offset));
        respVO.setPipelineRunId(runLog.getPipelineRunId());
        respVO.setRunLogId(runLog.getId());
        respVO.setStageId(runLog.getStageId());
        respVO.setJobId(runLog.getJobId());
        respVO.setStepId(runLog.getStepId());
        respVO.setLineNo(lineNo);
        respVO.setStreamType("stdout");
        respVO.setContent(limitLineContent(content));
        respVO.setCreateTime(LocalDateTime.now());
        return respVO;
    }

    private List<PipelineRunLogDO> findReadableRunLogs(Long pipelineRunId, String stepId) {
        if (StrUtil.isNotBlank(stepId)) {
            PipelineRunLogDO runLog = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(pipelineRunId, stepId);
            return runLog == null ? List.of() : List.of(runLog);
        }
        return pipelineRunLogMapper.selectListByPipelineRunId(pipelineRunId);
    }

    private void readRawLines(PipelineRunLogDO runLog, Long afterId, int queryLimit,
                              List<PipelineRunLogLineRespVO> result) {
        Path rawLog = rawLogFile(runLog);
        if (!Files.isRegularFile(rawLog)) {
            return;
        }
        long cursor = decodeOffset(runLog, afterId);
        try (RandomAccessFile file = new RandomAccessFile(rawLog.toFile(), "r")) {
            long lineNo = 0L;
            while (result.size() < queryLimit) {
                byte[] lineBytes = readLineBytes(file);
                if (lineBytes == null) {
                    break;
                }
                long lineEnd = file.getFilePointer();
                lineNo++;
                if (lineEnd <= cursor || lineBytes.length == 0) {
                    continue;
                }
                String content = new String(lineBytes, StandardCharsets.UTF_8);
                result.add(convert(runLog, content, lineNo, lineEnd));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Read pipeline log failed: " + ex.getMessage(), ex);
        }
    }

    private void archiveStepLog(PipelineRunLogDO runLog, Path rawLog, long size) {
        if (!archiveEnabled) {
            runLog.setLogArchiveStatus(PipelineLogArchiveStatus.LOCAL_ONLY);
            return;
        }
        if (size > archiveMaxBytes) {
            runLog.setLogArchiveStatus(PipelineLogArchiveStatus.SKIPPED_TOO_LARGE);
            return;
        }
        try {
            String fileName = "pipeline-run-" + runLog.getPipelineRunId() + "-log-" + runLog.getId() + ".log";
            String url = fileApi.createFile(Files.readAllBytes(rawLog), fileName, archiveDirectory, TEXT_PLAIN);
            runLog.setLogFileUrl(url);
            runLog.setLogArchiveStatus(PipelineLogArchiveStatus.UPLOADED);
            runLog.setLogArchivedAt(LocalDateTime.now());
        } catch (Exception ex) {
            runLog.setLogArchiveStatus(PipelineLogArchiveStatus.FAILED);
            log.warn("[archiveStepLog][runLogId({}) archive failed: {}]", runLog.getId(), ex.getMessage(), ex);
        }
    }

    private String limitLineContent(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxLineBytes) {
            return content;
        }
        byte[] markerBytes = TRUNCATED_MARKER.getBytes(StandardCharsets.UTF_8);
        if (maxLineBytes <= markerBytes.length) {
            return TRUNCATED_MARKER.substring(0, Math.max(0, maxLineBytes));
        }
        int limit = maxLineBytes - markerBytes.length;
        int endIndex = 0;
        int usedBytes = 0;
        while (endIndex < content.length()) {
            int codePoint = content.codePointAt(endIndex);
            int codePointBytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (usedBytes + codePointBytes > limit) {
                break;
            }
            usedBytes += codePointBytes;
            endIndex += Character.charCount(codePoint);
        }
        return content.substring(0, endIndex) + TRUNCATED_MARKER;
    }

    private Path rawLogFile(PipelineRunLogDO runLog) {
        if (StrUtil.isNotBlank(runLog.getLogFilePath())) {
            Path resolved = root().resolve(runLog.getLogFilePath()).normalize();
            if (!resolved.startsWith(root())) {
                throw new IllegalStateException("Pipeline log path is outside log root");
            }
            return resolved;
        }
        return root().resolve("tenants").resolve("tenant-" + safeId(runLog.getTenantId()))
                .resolve("runs").resolve("run-" + safeId(runLog.getPipelineRunId()))
                .resolve(STEP_LOG_DIRECTORY)
                .resolve("run-log-" + safeId(runLog.getId()) + "-" + safeStepId(runLog.getStepId()) + ".log")
                .normalize();
    }

    private Path root() {
        return Path.of(logRoot).toAbsolutePath().normalize();
    }

    private String relativeToRoot(Path path) {
        Path normalizedRoot = root();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRoot)) {
            throw new IllegalStateException("Pipeline log path is outside log root");
        }
        return normalizedRoot.relativize(normalizedPath).toString();
    }

    private String safeId(Long id) {
        return id == null ? "unknown" : String.valueOf(id);
    }

    private String safeStepId(String stepId) {
        return StrUtil.blankToDefault(stepId, "unknown").replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private long encodeCursor(PipelineRunLogDO runLog, long offset) {
        return runLog.getId() * CURSOR_OFFSET_FACTOR + offset;
    }

    private long decodeOffset(PipelineRunLogDO runLog, Long cursor) {
        if (cursor == null || cursor <= 0) {
            return 0L;
        }
        long runLogCursorPrefix = runLog.getId() * CURSOR_OFFSET_FACTOR;
        if (cursor < runLogCursorPrefix) {
            return 0L;
        }
        if (cursor >= runLogCursorPrefix + CURSOR_OFFSET_FACTOR) {
            return Long.MAX_VALUE;
        }
        return cursor - runLogCursorPrefix;
    }

    private long countLines(Path path) throws IOException {
        try (var stream = Files.lines(path, StandardCharsets.UTF_8)) {
            return stream.count();
        }
    }

    private byte[] readLineBytes(RandomAccessFile file) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        boolean seenAny = false;
        while (true) {
            int value;
            try {
                value = file.read();
            } catch (EOFException ex) {
                value = -1;
            }
            if (value == -1) {
                return seenAny ? buffer.toByteArray() : null;
            }
            seenAny = true;
            if (value == '\n') {
                return buffer.toByteArray();
            }
            if (value != '\r') {
                buffer.write(value);
            }
        }
    }

}
