package cn.iocoder.yudao.module.devops.service.pipeline.jenkins;

import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineJenkinsCallbackReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineJenkinsCallbackRespVO;

public interface PipelineJenkinsCallbackService {

    PipelineJenkinsCallbackRespVO handleCallback(Long pipelineRunId, String callbackToken,
                                                 PipelineJenkinsCallbackReqVO reqVO);

}
