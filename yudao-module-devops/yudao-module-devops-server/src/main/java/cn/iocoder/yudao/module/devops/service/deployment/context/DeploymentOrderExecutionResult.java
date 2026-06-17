package cn.iocoder.yudao.module.devops.service.deployment.context;

import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 部署单执行结果。
 */
@Data
@Builder
public class DeploymentOrderExecutionResult {

    /**
     * 部署单编号。
     */
    private Long deploymentOrderId;
    /**
     * 是否执行成功。
     */
    private boolean success;
    /**
     * 执行摘要。
     */
    private String summary;
    /**
     * 错误信息。
     */
    private String errorMessage;
    /**
     * 输出变量。
     */
    @Builder.Default
    private Map<String, Object> outputs = new LinkedHashMap<>();

}
