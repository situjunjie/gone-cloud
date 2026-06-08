package cn.iocoder.yudao.module.devops.service.pipeline.jenkins;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineJenkinsConsoleChunkRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.framework.jenkins.JenkinsConsoleChunk;
import cn.iocoder.yudao.module.devops.framework.jenkins.JenkinsPipelineClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_JENKINS_CONSOLE_UNAVAILABLE;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_RUN_NOT_EXISTS;

/**
 * Jenkins Console stream service.
 */
@Service
@Slf4j
public class PipelineJenkinsConsoleServiceImpl implements PipelineJenkinsConsoleService {

    private static final long SSE_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final long POLL_INTERVAL_MILLIS = 1000L;

    @Resource
    private PipelineRunMapper pipelineRunMapper;
    @Resource
    private JenkinsPipelineClient jenkinsPipelineClient;
    @Resource
    private TaskExecutor applicationTaskExecutor;

    @Override
    public SseEmitter streamConsole(Long pipelineRunId, Long start) {
        PipelineRunDO run = validatePipelineRunExists(pipelineRunId);
        if (!jenkinsPipelineClient.isEnabled()) {
            throw exception(PIPELINE_JENKINS_CONSOLE_UNAVAILABLE, "Jenkins 未启用");
        }
        if (StrUtil.isBlank(run.getJenkinsBuildNumber())) {
            throw exception(PIPELINE_JENKINS_CONSOLE_UNAVAILABLE, "Jenkins 构建编号尚未生成");
        }
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        Long safeStart = start == null || start < 0 ? 0L : start;
        applicationTaskExecutor.execute(() -> streamConsole(run.getId(), run.getJenkinsBuildNumber(), safeStart, emitter));
        return emitter;
    }

    private void streamConsole(Long pipelineRunId, String buildNumber, Long start, SseEmitter emitter) {
        Long offset = start;
        try {
            while (true) {
                JenkinsConsoleChunk chunk = jenkinsPipelineClient.getConsoleText(buildNumber, offset);
                offset = chunk.getNextStart();
                sendChunk(emitter, chunk, Boolean.TRUE.equals(chunk.getMoreData()) ? "log" : "complete");
                if (!Boolean.TRUE.equals(chunk.getMoreData())) {
                    emitter.complete();
                    return;
                }
                Thread.sleep(POLL_INTERVAL_MILLIS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (ServiceException ex) {
            sendError(emitter, ex);
            log.warn("[streamConsole][pipelineRunId({}) buildNumber({}) Jenkins console stream failed: {}]",
                    pipelineRunId, buildNumber, ex.getMessage());
            emitter.complete();
        } catch (Exception ex) {
            log.warn("[streamConsole][pipelineRunId({}) buildNumber({}) Jenkins console stream error]",
                    pipelineRunId, buildNumber, ex);
            emitter.completeWithError(ex);
        }
    }

    private void sendChunk(SseEmitter emitter, JenkinsConsoleChunk chunk, String eventName) throws IOException {
        PipelineJenkinsConsoleChunkRespVO respVO = new PipelineJenkinsConsoleChunkRespVO();
        respVO.setText(chunk.getText());
        respVO.setOffset(chunk.getNextStart());
        respVO.setHasMore(chunk.getMoreData());
        emitter.send(SseEmitter.event().name(eventName).data(success(respVO)));
    }

    private void sendError(SseEmitter emitter, ServiceException ex) {
        try {
            emitter.send(SseEmitter.event().name("error").data(CommonResult.error(ex)));
        } catch (IOException ignored) {
            // Client already disconnected.
        }
    }

    private PipelineRunDO validatePipelineRunExists(Long id) {
        PipelineRunDO run = pipelineRunMapper.selectById(id);
        if (run == null) {
            throw exception(PIPELINE_RUN_NOT_EXISTS);
        }
        return run;
    }

}
