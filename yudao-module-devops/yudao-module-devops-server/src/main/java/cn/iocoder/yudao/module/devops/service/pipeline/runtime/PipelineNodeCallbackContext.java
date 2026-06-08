package cn.iocoder.yudao.module.devops.service.pipeline.runtime;

import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineJenkinsCallbackReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 节点运行时回调上下文。
 */
@Data
@AllArgsConstructor
public class PipelineNodeCallbackContext {

    private PipelineRunDO run;
    private PipelineSpec.Node node;
    private PipelineJenkinsCallbackReqVO callback;

}
