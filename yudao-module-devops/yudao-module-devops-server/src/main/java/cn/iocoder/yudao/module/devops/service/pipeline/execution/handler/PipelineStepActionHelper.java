package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 流水线步骤动作写入辅助。
 */
@Component
public class PipelineStepActionHelper {

    public static final String RESULT_ACTIONS_KEY = "actions";

    /**
     * 将步骤动作合并到日志结果 JSON。
     *
     * @param runLog 步骤日志
     * @param actions 步骤动作
     */
    public void upsertActions(PipelineRunLogDO runLog, List<PipelineStepAction> actions) {
        if (runLog == null || CollUtil.isEmpty(actions)) {
            return;
        }
        Map<String, Object> resultJson = parseResult(runLog);
        List<Object> existingActions = readActionObjects(resultJson);
        for (PipelineStepAction action : actions) {
            if (action == null || StrUtil.isBlank(action.getKey())) {
                continue;
            }
            existingActions.removeIf(existing -> Objects.equals(action.getKey(), readActionKey(existing)));
            existingActions.add(JsonUtils.parseMap(JsonUtils.toJsonString(action)));
        }
        resultJson.put(RESULT_ACTIONS_KEY, existingActions);
        runLog.setResultJson(JsonUtils.toJsonString(resultJson));
    }

    /**
     * 从日志结果 JSON 移除指定动作。
     *
     * @param runLog 步骤日志
     * @param actionKeys 动作编码
     */
    public void removeActions(PipelineRunLogDO runLog, String... actionKeys) {
        if (runLog == null || actionKeys == null || actionKeys.length == 0) {
            return;
        }
        Map<String, Object> resultJson = parseResult(runLog);
        List<Object> existingActions = readActionObjects(resultJson);
        if (CollUtil.isEmpty(existingActions)) {
            return;
        }
        List<String> keys = List.of(actionKeys);
        existingActions.removeIf(existing -> keys.contains(readActionKey(existing)));
        if (existingActions.isEmpty()) {
            resultJson.remove(RESULT_ACTIONS_KEY);
        } else {
            resultJson.put(RESULT_ACTIONS_KEY, existingActions);
        }
        runLog.setResultJson(JsonUtils.toJsonString(resultJson));
    }

    /**
     * 合并 result JSON 和步骤处理结果。
     *
     * @param runLog 步骤日志
     * @param result 步骤处理结果
     */
    public void mergeStepResult(PipelineRunLogDO runLog, StepResult result) {
        if (runLog == null || result == null) {
            return;
        }
        Map<String, Object> resultJson = parseResult(runLog);
        if (CollUtil.isNotEmpty(result.getOutputs())) {
            resultJson.put("outputs", new LinkedHashMap<>(result.getOutputs()));
        }
        runLog.setResultJson(JsonUtils.toJsonString(resultJson));
        upsertActions(runLog, result.getActions());
    }

    private Map<String, Object> parseResult(PipelineRunLogDO runLog) {
        Map<String, Object> resultJson = JsonUtils.parseMap(runLog.getResultJson());
        return CollUtil.isEmpty(resultJson) ? new LinkedHashMap<>() : new LinkedHashMap<>(resultJson);
    }

    private List<Object> readActionObjects(Map<String, Object> resultJson) {
        Object value = resultJson.get(RESULT_ACTIONS_KEY);
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(list);
    }

    private String readActionKey(Object action) {
        if (action instanceof Map<?, ?> map) {
            Object key = map.get("key");
            return key == null ? null : String.valueOf(key);
        }
        return null;
    }

}
