package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流水线步骤处理结果。
 */
@Data
@Builder
public class StepResult {

    /**
     * 处理结果类型。
     */
    private StepResultType type;
    /**
     * 处理摘要。
     */
    private String summary;
    /**
     * 错误编码。
     */
    private String errorCode;
    /**
     * 错误信息。
     */
    private String errorMessage;
    /**
     * 步骤输出变量。
     */
    @Builder.Default
    private Map<String, Object> outputs = new LinkedHashMap<>();
    /**
     * 步骤可操作点。
     */
    @Builder.Default
    private List<PipelineStepAction> actions = List.of();

    public static StepResult continueWith(String summary) {
        return StepResult.builder().type(StepResultType.CONTINUE).summary(summary).build();
    }

    public static StepResult suspend(String summary) {
        return StepResult.builder().type(StepResultType.SUSPEND).summary(summary).build();
    }

    public static StepResult fail(String summary, String errorMessage) {
        return StepResult.builder()
                .type(StepResultType.FAIL)
                .summary(summary)
                .errorMessage(errorMessage)
                .build();
    }

}
