package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流水线步骤可操作点。
 */
@Data
@Builder
public class PipelineStepAction {

    /**
     * 动作稳定编码。
     */
    private String key;
    /**
     * 动作展示文案。
     */
    private String label;
    /**
     * 动作类型。
     */
    private String type;
    /**
     * 动作样式。
     */
    private String style;
    /**
     * 权限标识。
     */
    private String permission;
    /**
     * 是否可用。
     */
    @Builder.Default
    private Boolean enabled = true;
    /**
     * 不可用原因。
     */
    private String disabledReason;
    /**
     * 动作目标。
     */
    private PipelineStepActionTarget target;
    /**
     * 展示辅助数据。
     */
    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<>();
    /**
     * 排序。
     */
    private Integer order;

}
