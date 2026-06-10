package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 部署单执行阶段，用于部署单当前阶段摘要，不单独持久化为子表。
 */
@Getter
@AllArgsConstructor
public enum DeploymentOrderStepKeyEnum {

    PREPARE_CONTEXT("PREPARE_CONTEXT", "准备部署上下文"),
    CONNECT_CLUSTER("CONNECT_CLUSTER", "连接 Kubernetes 集群"),
    LOAD_WORKLOAD("LOAD_WORKLOAD", "读取 Deployment"),
    APPLY_SPEC("APPLY_SPEC", "提交 Deployment 变更"),
    WAIT_ROLLOUT("WAIT_ROLLOUT", "等待 rollout ready"),
    CAPTURE_RESULT("CAPTURE_RESULT", "记录部署结果");

    private final String key;
    private final String name;

}
