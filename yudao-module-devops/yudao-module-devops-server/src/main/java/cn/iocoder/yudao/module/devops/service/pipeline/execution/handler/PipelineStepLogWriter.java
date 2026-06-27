package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineRunLogLineService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * 流水线步骤实时日志写入工具。
 */
@Slf4j
public final class PipelineStepLogWriter {

    private PipelineStepLogWriter() {
    }

    public static void appendLine(PipelineRunLogDO runLog, Path logFile, List<String> logLines,
                                  int maxLogLines, PipelineRunLogLineService lineService, String line) {
        appendLine(runLog, logFile, logLines, maxLogLines, lineService, "stdout", line);
    }

    public static void appendLine(PipelineRunLogDO runLog, Path logFile, List<String> logLines,
                                  int maxLogLines, PipelineRunLogLineService lineService,
                                  String streamType, String line) {
        if (StrUtil.isBlank(line)) {
            return;
        }
        appendToFile(runLog, logFile, line);
        publish(runLog, lineService, streamType, line);
        appendSummary(logLines, maxLogLines, line);
    }

    public static void appendRealtimeLine(PipelineRunLogDO runLog, List<String> logLines, int maxLogLines,
                                          PipelineRunLogLineService lineService, String streamType, String line) {
        if (StrUtil.isBlank(line)) {
            return;
        }
        publish(runLog, lineService, streamType, line);
        appendSummary(logLines, maxLogLines, line);
    }

    private static void publish(PipelineRunLogDO runLog, PipelineRunLogLineService lineService,
                                String streamType, String line) {
        if (lineService == null) {
            return;
        }
        try {
            lineService.appendLine(runLog, streamType, line);
        } catch (Exception ex) {
            Long runLogId = runLog == null ? null : runLog.getId();
            log.warn("[publish][runLogId({}) publish pipeline step log failed: {}]", runLogId,
                    ex.getMessage(), ex);
        }
    }

    private static void appendSummary(List<String> logLines, int maxLogLines, String line) {
        if (logLines != null && logLines.size() < maxLogLines) {
            logLines.add(line);
        }
    }

    private static void appendToFile(PipelineRunLogDO runLog, Path logFile, String line) {
        if (logFile == null) {
            return;
        }
        try {
            Path parent = logFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            Long runLogId = runLog == null ? null : runLog.getId();
            log.warn("[appendToFile][runLogId({}) write pipeline step log failed: {}]", runLogId,
                    ex.getMessage(), ex);
        }
    }

}
