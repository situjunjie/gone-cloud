package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 流水线步骤输出变量注册表。
 *
 * <p>输出变量按 {@code jobId -> stepId -> key} 命名空间存储，避免并发任务写入同名 key 时互相覆盖。
 */
public class PipelineOutputRegistry {

    /**
     * 任务编号 -> 步骤编号 -> 输出变量。
     */
    private final ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<String, Object>>> outputs =
            new ConcurrentHashMap<>();

    /**
     * 发布一个步骤的输出变量。
     *
     * @param jobId   任务编号
     * @param stepId  步骤编号
     * @param values  输出变量
     */
    public void publish(String jobId, String stepId, Map<String, Object> values) {
        if (StrUtil.isBlank(jobId) || StrUtil.isBlank(stepId) || CollUtil.isEmpty(values)) {
            return;
        }
        ConcurrentMap<String, Object> stepOutputs = outputs
                .computeIfAbsent(jobId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(stepId, ignored -> new ConcurrentHashMap<>());
        values.forEach((key, value) -> {
            if (StrUtil.isNotBlank(key) && value != null) {
                stepOutputs.put(key, value);
            }
        });
    }

    /**
     * 从持久化快照恢复输出变量。
     *
     * @param snapshot 输出快照
     */
    public void restore(Map<String, Map<String, Map<String, Object>>> snapshot) {
        if (CollUtil.isEmpty(snapshot)) {
            return;
        }
        snapshot.forEach((jobId, jobOutputs) -> {
            if (CollUtil.isEmpty(jobOutputs)) {
                return;
            }
            jobOutputs.forEach((stepId, stepOutputs) -> publish(jobId, stepId, stepOutputs));
        });
    }

    /**
     * 获取一个步骤输出变量。
     *
     * @param jobId  任务编号
     * @param stepId 步骤编号
     * @param key    输出变量名
     * @return 输出值；不存在时返回 null
     */
    public Object get(String jobId, String stepId, String key) {
        Map<String, ConcurrentMap<String, Object>> jobOutputs = outputs.get(jobId);
        if (jobOutputs == null) {
            return null;
        }
        Map<String, Object> stepOutputs = jobOutputs.get(stepId);
        return stepOutputs == null ? null : stepOutputs.get(key);
    }

    /**
     * 快照化全部输出变量。
     *
     * @return 可序列化的输出变量结构
     */
    public Map<String, Map<String, Map<String, Object>>> snapshot() {
        Map<String, Map<String, Map<String, Object>>> snapshot = new LinkedHashMap<>();
        outputs.forEach((jobId, jobOutputs) -> {
            Map<String, Map<String, Object>> jobSnapshot = new LinkedHashMap<>();
            jobOutputs.forEach((stepId, stepOutputs) -> jobSnapshot.put(stepId, new LinkedHashMap<>(stepOutputs)));
            snapshot.put(jobId, jobSnapshot);
        });
        return snapshot;
    }

    /**
     * 扁平化唯一输出变量，兼容既有 {@code ${key}} 引用。
     *
     * <p>并发任务产出的同名 key 不进入扁平结果，避免无命名空间覆盖。
     *
     * @return 唯一 key 输出变量
     */
    public Map<String, Object> flattenUniqueVariables() {
        Map<String, Object> flattened = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        outputs.forEach((jobId, jobOutputs) -> jobOutputs.forEach((stepId, stepOutputs) ->
                stepOutputs.forEach((key, value) -> {
                    counts.merge(key, 1, Integer::sum);
                    flattened.putIfAbsent(key, value);
                })));
        counts.forEach((key, count) -> {
            if (count > 1) {
                flattened.remove(key);
            }
        });
        return flattened;
    }

}
