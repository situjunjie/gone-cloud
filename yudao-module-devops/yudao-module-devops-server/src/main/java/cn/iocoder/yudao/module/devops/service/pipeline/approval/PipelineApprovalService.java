package cn.iocoder.yudao.module.devops.service.pipeline.approval;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;

public interface PipelineApprovalService {

    String BUSINESS_KEY_PREFIX = "devops:pipeline-approval:";

    boolean isApprovalBusinessKey(String businessKey);

    void startApproval(PipelineRunDO run, PipelineSpec.Node node, Long userId);

    PipelineApprovalStatusHandleResult handleProcessInstanceStatus(BpmProcessInstanceStatusEvent event);

}
