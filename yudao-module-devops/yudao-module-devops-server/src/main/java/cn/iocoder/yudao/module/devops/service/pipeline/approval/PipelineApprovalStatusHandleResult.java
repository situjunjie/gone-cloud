package cn.iocoder.yudao.module.devops.service.pipeline.approval;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * BPM 审批状态回调处理结果。
 */
@Data
@AllArgsConstructor
public class PipelineApprovalStatusHandleResult {

    private Boolean handled;
    private Boolean approved;
    private PipelineRunDO run;

    public static PipelineApprovalStatusHandleResult ignored() {
        return new PipelineApprovalStatusHandleResult(false, false, null);
    }

    public static PipelineApprovalStatusHandleResult handled(boolean approved, PipelineRunDO run) {
        return new PipelineApprovalStatusHandleResult(true, approved, run);
    }

}
