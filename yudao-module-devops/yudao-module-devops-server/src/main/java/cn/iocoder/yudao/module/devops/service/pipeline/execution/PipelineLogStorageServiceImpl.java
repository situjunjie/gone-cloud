package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
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
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_RUN_LOG_FILE_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_RUN_LOG_NOT_EXISTS;

/**
 * 流水线工作目录文件日志存储服务实现。
 */
@Slf4j
@Service
@Validated
public class PipelineLogStorageServiceImpl implements PipelineLogStorageService {

    private static final String LOG_DIRECTORY = ".devops/pipeline-runs";
    private static final String TEXT_PLAIN = "text/plain";
    private static final String TRUNCATED_MARKER = "... [line truncated]";
    private static final long CURSOR_OFFSET_FACTOR = 1_000_000_000_000L;

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
        validateWorkspaceReady(runLog);
        Path rawLog = rawLogFile(runLog);
        try {
            Files.createDirectories(rawLog.getParent());
            if (!Files.exists(rawLog)) {
                Files.createFile(rawLog);
            }
            String relativePath = relativeLogPath(runLog);
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
        validateWorkspaceReady(runLog);
        return rawLogFile(runLog);
    }

    @Override
    public PipelineRunLogLineRespVO appendStepLogLine(PipelineRunLogDO runLog, String streamType, String content) {
        if (runLog == null || runLog.getPipelineRunId() == null || StrUtil.isBlank(content)) {
            return null;
        }
        try {
            Path rawLog = prepareStepLog(runLog);
            long size = Files.size(rawLog);
            if (size >= maxRunBytes) {
                markTruncated(runLog);
                return null;
            }
            String normalizedStreamType = "stderr".equalsIgnoreCase(streamType) ? "stderr" : "stdout";
            String line = limitLineContent(content);
            byte[] bytes = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            if (size + bytes.length > maxRunBytes) {
                markTruncated(runLog);
                return null;
            }
            long lineNo = countLines(rawLog) + 1;
            Files.write(rawLog, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            long offset = Files.size(rawLog);
            PipelineRunLogLineRespVO respVO = convert(runLog, line, normalizedStreamType, lineNo, offset);
            runLog.setLogSizeBytes(offset);
            runLog.setLogLineCount(lineNo);
            return respVO;
        } catch (IOException ex) {
            throw new IllegalStateException("Write pipeline log failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<PipelineRunLogLineRespVO> readLines(Long pipelineRunId, String stepId, Long afterId, Integer limit) {
        int queryLimit = limit == null || limit <= 0 ? 200 : Math.min(limit, 500);
        PipelineRunLogDO runLog = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(pipelineRunId, stepId);
        if (runLog == null) {
            return List.of();
        }
        List<PipelineRunLogLineRespVO> result = new ArrayList<>(queryLimit);
        readRawLines(runLog, afterId, queryLimit, result);
        return result;
    }

    @Override
    public boolean hasStepLogFile(Long pipelineRunId, String stepId) {
        PipelineRunLogDO runLog = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(pipelineRunId, stepId);
        if (runLog == null || StrUtil.isBlank(runLog.getWorkspacePath())) {
            return false;
        }
        return Files.isRegularFile(rawLogFile(runLog));
    }

    @Override
    public void completeStepLog(PipelineRunLogDO runLog) {
        if (runLog == null || runLog.getId() == null || runLog.getPipelineRunId() == null
                || StrUtil.isBlank(runLog.getWorkspacePath())) {
            return;
        }
        try {
            Path rawLog = rawLogFile(runLog);
            if (!Files.isRegularFile(rawLog)) {
                return;
            }
            refreshLocalMetadata(runLog, rawLog);
            pipelineRunLogMapper.updateById(runLog);
        } catch (Exception ex) {
            log.warn("[completeStepLog][runLogId({}) complete log failed: {}]", runLog.getId(), ex.getMessage(), ex);
        }
    }

    @Override
    public void archiveStepLog(PipelineRunLogDO runLog) {
        if (runLog == null || runLog.getId() == null || runLog.getPipelineRunId() == null
                || StrUtil.isBlank(runLog.getWorkspacePath())) {
            return;
        }
        try {
            Path rawLog = rawLogFile(runLog);
            if (!Files.isRegularFile(rawLog)) {
                return;
            }
            refreshLocalMetadata(runLog, rawLog);
            archiveStepLog(runLog, rawLog, runLog.getLogSizeBytes() == null ? 0L : runLog.getLogSizeBytes());
            pipelineRunLogMapper.updateById(runLog);
        } catch (Exception ex) {
            runLog.setLogArchiveStatus(PipelineLogArchiveStatus.FAILED);
            pipelineRunLogMapper.updateById(runLog);
            log.warn("[archiveStepLog][runLogId({}) archive failed: {}]", runLog.getId(), ex.getMessage(), ex);
        }
    }

    @Override
    public void archiveRunLogs(PipelineRunDO run) {
        if (run == null || run.getId() == null) {
            return;
        }
        for (PipelineRunLogDO runLog : pipelineRunLogMapper.selectListByPipelineRunId(run.getId())) {
            if (StrUtil.isNotBlank(runLog.getLogFileUrl())) {
                continue;
            }
            archiveStepLog(runLog);
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

    private PipelineRunLogLineRespVO convert(PipelineRunLogDO runLog, String content, String streamType,
                                             long lineNo, long offset) {
        PipelineRunLogLineRespVO respVO = new PipelineRunLogLineRespVO();
        respVO.setId(encodeCursor(runLog, offset));
        respVO.setPipelineRunId(runLog.getPipelineRunId());
        respVO.setRunLogId(runLog.getId());
        respVO.setStageId(runLog.getStageId());
        respVO.setJobId(runLog.getJobId());
        respVO.setStepId(runLog.getStepId());
        respVO.setLineNo(lineNo);
        respVO.setStreamType(streamType);
        respVO.setContent(limitLineContent(content));
        respVO.setCreateTime(LocalDateTime.now());
        return respVO;
    }

    private void readRawLines(PipelineRunLogDO runLog, Long afterId, int queryLimit,
                              List<PipelineRunLogLineRespVO> result) {
        if (StrUtil.isBlank(runLog.getWorkspacePath())) {
            return;
        }
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
                result.add(convert(runLog, content, "stdout", lineNo, lineEnd));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Read pipeline log failed: " + ex.getMessage(), ex);
        }
    }

    private void refreshLocalMetadata(PipelineRunLogDO runLog, Path rawLog) throws IOException {
        long size = Files.size(rawLog);
        long lineCount = countLines(rawLog);
        runLog.setLogFilePath(relativeLogPath(runLog));
        runLog.setLogSizeBytes(size);
        runLog.setLogLineCount(lineCount);
        if (size > maxRunBytes) {
            runLog.setLogTruncated(true);
        }
        if (StrUtil.isBlank(runLog.getLogArchiveStatus())) {
            runLog.setLogArchiveStatus(PipelineLogArchiveStatus.PENDING);
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
            String fileName = "pipeline-run-" + runLog.getPipelineRunId() + "-step-" + safeStepId(runLog.getStepId()) + ".log";
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
        Path workspace = workspace(runLog);
        Path resolved = workspace.resolve(StrUtil.blankToDefault(runLog.getLogFilePath(), relativeLogPath(runLog))).normalize();
        if (!resolved.startsWith(workspace)) {
            throw new IllegalStateException("Pipeline log path is outside workspace");
        }
        return resolved;
    }

    private String relativeLogPath(PipelineRunLogDO runLog) {
        return LOG_DIRECTORY + "/run-" + safeId(runLog.getPipelineRunId())
                + "/logs/" + safeStepId(runLog.getStageId())
                + "/" + safeStepId(runLog.getJobId())
                + "/" + safeStepId(runLog.getStepId()) + ".log";
    }

    private Path workspace(PipelineRunLogDO runLog) {
        return Path.of(runLog.getWorkspacePath()).toAbsolutePath().normalize();
    }

    private void validateWorkspaceReady(PipelineRunLogDO runLog) {
        if (runLog == null || runLog.getId() == null || runLog.getPipelineRunId() == null
                || StrUtil.isBlank(runLog.getWorkspacePath())) {
            throw new IllegalArgumentException("runLog workspace is required");
        }
    }

    private void markTruncated(PipelineRunLogDO runLog) {
        runLog.setLogTruncated(true);
        pipelineRunLogMapper.updateById(runLog);
    }

    private String safeId(Long id) {
        return id == null ? "unknown" : String.valueOf(id);
    }

    private String safeStepId(String value) {
        return StrUtil.blankToDefault(value, "unknown").replaceAll("[^A-Za-z0-9_.-]", "_");
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
