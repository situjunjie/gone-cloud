package cn.iocoder.yudao.module.devops.service.pipeline.plugin;

import cn.iocoder.yudao.module.devops.enums.PipelineRunJobStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 流水线插件触发状态。
 */
@Getter
@AllArgsConstructor
public enum PipelinePluginTriggerState {

    SUCCESS("success", PipelineRunJobStatusEnum.SUCCESS.getStatus()),
    FAIL("fail", PipelineRunJobStatusEnum.FAILED.getStatus());

    /** DSL 状态值。 */
    private final String value;
    /** Job 运行状态。 */
    private final String jobStatus;

    /**
     * 判断 DSL 状态是否受支持。
     *
     * @param value DSL 状态值
     * @return 是否受支持
     */
    public static boolean supports(String value) {
        return Arrays.stream(values()).anyMatch(state -> state.value.equals(value));
    }

    /**
     * 根据 Job 状态解析 DSL 触发状态。
     *
     * @param jobStatus Job 状态
     * @return DSL 触发状态；非触发终态返回 null
     */
    public static String fromJobStatus(String jobStatus) {
        return Arrays.stream(values()).filter(state -> state.jobStatus.equals(jobStatus))
                .map(PipelinePluginTriggerState::getValue).findFirst().orElse(null);
    }

}
