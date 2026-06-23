package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * 流水线运行日志文件存储。
 */
@Component
public class PipelineRunLogFileStorage {

    private static final String CONTAINER_WORKSPACE = "/workspace";
    private static final String LOG_ROOT = ".gone-devops/logs";
    private static final String STDOUT = "stdout";
    private static final String STDERR = "stderr";
    private static final String STDOUT_FILE = "stdout.log";
    private static final String STDERR_FILE = "stderr.log";
    private static final String FULL_LOG_FILE = "full.log";

    private final ConcurrentMap<Long, Object> locks = new ConcurrentHashMap<>();

    /**
     * 准备指定步骤的日志文件。
     *
     * @param runLog 步骤运行日志
     * @param workspace 宿主机 run workspace
     * @return 日志文件描述
     */
    public PipelineRunLogFiles prepare(PipelineRunLogDO runLog, Path workspace) {
        if (runLog == null || runLog.getId() == null || workspace == null) {
            return null;
        }
        Path directory = directory(workspace, runLog.getId());
        try {
            Files.createDirectories(directory);
            Files.deleteIfExists(directory.resolve(STDOUT_FILE));
            Files.deleteIfExists(directory.resolve(STDERR_FILE));
            Files.deleteIfExists(directory.resolve(FULL_LOG_FILE));
            Files.createFile(directory.resolve(STDOUT_FILE));
            Files.createFile(directory.resolve(STDERR_FILE));
            return buildFiles(runLog.getId(), directory);
        } catch (IOException ex) {
            throw new IllegalStateException("Prepare pipeline log files failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 获取指定步骤已有的日志文件。
     *
     * @param runLog 步骤运行日志
     * @return 日志文件描述；不存在时返回 null
     */
    public PipelineRunLogFiles find(PipelineRunLogDO runLog) {
        if (runLog == null || runLog.getId() == null || StrUtil.isBlank(runLog.getWorkspacePath())) {
            return null;
        }
        Path directory = directory(Path.of(runLog.getWorkspacePath()), runLog.getId());
        if (!Files.exists(directory.resolve(STDOUT_FILE)) && !Files.exists(directory.resolve(STDERR_FILE))) {
            return null;
        }
        return buildFiles(runLog.getId(), directory);
    }

    /**
     * 追加一行日志到步骤日志文件。
     *
     * @param runLog 步骤运行日志
     * @param streamType 输出流类型
     * @param content 日志内容
     * @return 追加后的行响应；无法写文件时返回 null
     */
    public PipelineRunLogLineRespVO appendLine(PipelineRunLogDO runLog, String streamType, String content) {
        if (runLog == null || runLog.getId() == null || StrUtil.isBlank(runLog.getWorkspacePath())
                || StrUtil.isBlank(content)) {
            return null;
        }
        PipelineRunLogFiles files = find(runLog);
        if (files == null) {
            files = prepare(runLog, Path.of(runLog.getWorkspacePath()));
        }
        Object lock = locks.computeIfAbsent(runLog.getId(), key -> new Object());
        synchronized (lock) {
            try {
                String normalizedStream = normalizeStream(streamType);
                Path file = STDERR.equals(normalizedStream) ? files.getStderrPath() : files.getStdoutPath();
                Files.writeString(file, StrUtil.subPre(content, 2000) + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                long lineNo = countLines(files.getStdoutPath()) + countLines(files.getStderrPath());
                return buildLine(runLog, lineNo, lineNo, normalizedStream, StrUtil.subPre(content, 2000));
            } catch (IOException ex) {
                throw new IllegalStateException("Append pipeline log file failed: " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * 判断是否存在文件日志。
     *
     * @param runLogs 步骤运行日志列表
     * @return 是否存在
     */
    public boolean hasAnyLogFile(List<PipelineRunLogDO> runLogs) {
        if (runLogs == null) {
            return false;
        }
        for (PipelineRunLogDO runLog : runLogs) {
            PipelineRunLogFiles files = find(runLog);
            if (files != null && (hasContent(files.getStdoutPath()) || hasContent(files.getStderrPath()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 读取指定游标后的日志行。
     *
     * @param runLogs 步骤运行日志列表
     * @param stepId 步骤编号，可空
     * @param afterId 游标
     * @param limit 返回条数
     * @return 日志行列表
     */
    public List<PipelineRunLogLineRespVO> readLines(List<PipelineRunLogDO> runLogs, String stepId, Long afterId,
                                                    int limit) {
        List<PipelineRunLogLineRespVO> result = new ArrayList<>();
        if (runLogs == null || limit <= 0) {
            return result;
        }
        List<PipelineRunLogDO> sortedLogs = runLogs.stream()
                .filter(runLog -> StrUtil.isBlank(stepId) || stepId.equals(runLog.getStepId()))
                .sorted(Comparator.comparing(PipelineRunLogDO::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        long cursor = 0L;
        long minId = afterId == null ? 0L : afterId;
        for (PipelineRunLogDO runLog : sortedLogs) {
            List<PipelineRunLogLineRespVO> allLines = readLines(runLog, null, Integer.MAX_VALUE);
            for (PipelineRunLogLineRespVO line : allLines) {
                cursor++;
                line.setId(cursor);
                if (cursor <= minId) {
                    continue;
                }
                result.add(line);
                if (result.size() >= limit) {
                    return result;
                }
            }
        }
        return result;
    }

    /**
     * 生成并读取完整日志文件内容。
     *
     * @param runLog 步骤运行日志
     * @return 完整日志文件内容
     */
    public byte[] buildFullLog(PipelineRunLogDO runLog) {
        PipelineRunLogFiles files = find(runLog);
        if (files == null) {
            return new byte[0];
        }
        try {
            List<String> lines = new ArrayList<>();
            for (PipelineRunLogLineRespVO line : readLines(runLog, null, Integer.MAX_VALUE)) {
                lines.add("[" + line.getStreamType() + "] " + line.getContent());
            }
            String content = String.join(System.lineSeparator(), lines);
            if (!content.isEmpty()) {
                content += System.lineSeparator();
            }
            Files.writeString(files.getFullPath(), content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return content.getBytes(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Build full pipeline log file failed: " + ex.getMessage(), ex);
        }
    }

    private List<PipelineRunLogLineRespVO> readLines(PipelineRunLogDO runLog, Long afterId, int limit) {
        List<PipelineRunLogLineRespVO> result = new ArrayList<>();
        PipelineRunLogFiles files = find(runLog);
        if (files == null || limit <= 0) {
            return result;
        }
        long cursor = 0L;
        cursor = appendFileLines(result, runLog, files.getStdoutPath(), STDOUT, cursor, afterId, limit);
        if (result.size() >= limit) {
            return result;
        }
        appendFileLines(result, runLog, files.getStderrPath(), STDERR, cursor, afterId, limit);
        return result;
    }

    private long appendFileLines(List<PipelineRunLogLineRespVO> result, PipelineRunLogDO runLog, Path file,
                                 String streamType, long cursor, Long afterId, int limit) {
        if (!Files.exists(file)) {
            return cursor;
        }
        long minId = afterId == null ? 0L : afterId;
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            for (String content : lines.toList()) {
                cursor++;
                if (cursor <= minId || result.size() >= limit) {
                    continue;
                }
                result.add(buildLine(runLog, cursor, cursor, streamType, content));
            }
            return cursor;
        } catch (IOException ex) {
            throw new IllegalStateException("Read pipeline log file failed: " + ex.getMessage(), ex);
        }
    }

    private PipelineRunLogLineRespVO buildLine(PipelineRunLogDO runLog, Long id, Long lineNo, String streamType,
                                               String content) {
        PipelineRunLogLineRespVO respVO = new PipelineRunLogLineRespVO();
        respVO.setId(id);
        respVO.setPipelineRunId(runLog.getPipelineRunId());
        respVO.setRunLogId(runLog.getId());
        respVO.setStageId(runLog.getStageId());
        respVO.setJobId(runLog.getJobId());
        respVO.setStepId(runLog.getStepId());
        respVO.setLineNo(lineNo);
        respVO.setStreamType(streamType);
        respVO.setContent(content);
        respVO.setCreateTime(runLog.getCreateTime() == null ? LocalDateTime.now() : runLog.getCreateTime());
        return respVO;
    }

    private PipelineRunLogFiles buildFiles(Long runLogId, Path directory) {
        PipelineRunLogFiles files = new PipelineRunLogFiles();
        files.setDirectory(directory);
        files.setStdoutPath(directory.resolve(STDOUT_FILE));
        files.setStderrPath(directory.resolve(STDERR_FILE));
        files.setFullPath(directory.resolve(FULL_LOG_FILE));
        String containerDir = CONTAINER_WORKSPACE + "/" + LOG_ROOT + "/run-log-" + runLogId;
        files.setContainerStdoutPath(containerDir + "/" + STDOUT_FILE);
        files.setContainerStderrPath(containerDir + "/" + STDERR_FILE);
        return files;
    }

    private Path directory(Path workspace, Long runLogId) {
        return workspace.resolve(LOG_ROOT).resolve("run-log-" + runLogId).normalize();
    }

    private boolean hasContent(Path path) {
        try {
            return Files.exists(path) && Files.size(path) > 0;
        } catch (IOException ex) {
            return false;
        }
    }

    private long countLines(Path path) throws IOException {
        if (!Files.exists(path)) {
            return 0L;
        }
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines.count();
        }
    }

    private String normalizeStream(String streamType) {
        return STDERR.equalsIgnoreCase(streamType) ? STDERR : STDOUT;
    }

    @Data
    public static class PipelineRunLogFiles {

        private Path directory;
        private Path stdoutPath;
        private Path stderrPath;
        private Path fullPath;
        private String containerStdoutPath;
        private String containerStderrPath;

    }

}
