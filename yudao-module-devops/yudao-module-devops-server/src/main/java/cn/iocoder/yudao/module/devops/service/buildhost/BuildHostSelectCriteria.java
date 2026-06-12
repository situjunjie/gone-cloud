package cn.iocoder.yudao.module.devops.service.buildhost;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * 构建主机选择条件。
 *
 * <p>v1 调度只用到默认选择，但接口预留 label 过滤与容量维度，供后续多机调度扩展。
 */
@Data
@Builder
public class BuildHostSelectCriteria {

    /**
     * 期望的主机标签集合。为空表示不限制；非空时要求主机标签包含全部所需标签。
     */
    private Set<String> requiredLabels;

    /**
     * 期望的主机类型（{@code SSH} / {@code LOCAL}）。为空表示不限制。
     */
    private String preferredType;

    /**
     * 本次构建预计占用的并发数，供容量调度参考。默认按 1 计。
     */
    private Integer requiredConcurrency;

    public static BuildHostSelectCriteria none() {
        return BuildHostSelectCriteria.builder().build();
    }

}
