package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PipelineStepActionHelper} 的单元测试。
 */
class PipelineStepActionHelperTest {

    private final PipelineStepActionHelper helper = new PipelineStepActionHelper();

    @Test
    void testUpsertActions_keepExistingResultAndReplaceSameKey() {
        PipelineRunLogDO runLog = new PipelineRunLogDO();
        runLog.setResultJson(JsonUtils.toJsonString(Map.of(
                "storagePath", "s3://bucket/old.tar",
                "actions", List.of(Map.of(
                        "key", PipelineStepActionKeyConstants.IMAGE_ARCHIVE_DOWNLOAD,
                        "label", "旧下载")))));

        helper.upsertActions(runLog, List.of(PipelineStepAction.builder()
                .key(PipelineStepActionKeyConstants.IMAGE_ARCHIVE_DOWNLOAD)
                .label("下载镜像")
                .type(PipelineStepActionTypeEnum.DOWNLOAD.getType())
                .target(PipelineStepActionTarget.builder().url("s3://bucket/new.tar").build())
                .build()));

        Map<String, Object> resultJson = JsonUtils.parseMap(runLog.getResultJson());
        assertEquals("s3://bucket/old.tar", resultJson.get("storagePath"));
        List<?> actions = (List<?>) resultJson.get("actions");
        assertEquals(1, actions.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> action = (Map<String, Object>) actions.get(0);
        assertEquals("下载镜像", action.get("label"));
        assertFalse(runLog.getResultJson().contains("旧下载"));
    }

    @Test
    void testRemoveActions_removeActionsKeyWhenEmpty() {
        PipelineRunLogDO runLog = new PipelineRunLogDO();
        runLog.setResultJson(JsonUtils.toJsonString(Map.of(
                "outputs", Map.of("image", "demo:1.0"),
                "actions", List.of(Map.of(
                        "key", PipelineStepActionKeyConstants.IMAGE_ARCHIVE_DOWNLOAD,
                        "label", "下载镜像")))));

        helper.removeActions(runLog, PipelineStepActionKeyConstants.IMAGE_ARCHIVE_DOWNLOAD);

        Map<String, Object> resultJson = JsonUtils.parseMap(runLog.getResultJson());
        assertTrue(resultJson.containsKey("outputs"));
        assertFalse(resultJson.containsKey("actions"));
    }

    @Test
    void testMergeStepResult_outputsAndActions() {
        PipelineRunLogDO runLog = new PipelineRunLogDO();

        helper.mergeStepResult(runLog, StepResult.builder()
                .outputs(Map.of("mergedBranch", "release/test"))
                .actions(List.of(PipelineStepAction.builder()
                        .key(PipelineStepActionKeyConstants.CODE_MERGE_RESOLVE_CONFLICT)
                        .label("解决冲突")
                        .type(PipelineStepActionTypeEnum.ROUTE.getType())
                        .build()))
                .build());

        Map<String, Object> resultJson = JsonUtils.parseMap(runLog.getResultJson());
        @SuppressWarnings("unchecked")
        Map<String, Object> outputs = (Map<String, Object>) resultJson.get("outputs");
        assertEquals("release/test", outputs.get("mergedBranch"));
        assertEquals(1, ((List<?>) resultJson.get("actions")).size());
    }

}
