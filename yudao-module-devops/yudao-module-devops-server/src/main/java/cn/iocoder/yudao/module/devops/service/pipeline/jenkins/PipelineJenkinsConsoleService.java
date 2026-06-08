package cn.iocoder.yudao.module.devops.service.pipeline.jenkins;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface PipelineJenkinsConsoleService {

    SseEmitter streamConsole(Long pipelineRunId, Long start);

}
