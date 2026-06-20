package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineVariableResolver;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link PipelineVariableResolver} 的单元测试。
 */
class PipelineVariableResolverTest {

    @Test
    void testResolveStepWith_nestedMapAndList() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setCommitSha("abc123");
        PipelineSpec.ExecutableJob job = new PipelineSpec.ExecutableJob();
        job.setStageId("build_stage");
        job.setJobId("build_job");
        PipelineSpec.ExecutableStep step = new PipelineSpec.ExecutableStep();
        step.setStepId("build_step");
        Map<String, Object> with = new LinkedHashMap<>();
        with.put("image", "registry.example.com/ns/demo:${COMMIT_SHA}");
        with.put("env", Map.of("RUN_ID", "${runId}", "JOB_ID", "${JOB_ID}"));
        with.put("variables", List.of(Map.of("key", "TAG", "value", "${imageTag}")));
        step.setWith(with);
        Map<String, Object> sharedState = new ConcurrentHashMap<>();
        sharedState.put("imageTag", "v1.0.0");
        PipelineStepContext ctx = PipelineStepContext.builder()
                .run(run)
                .job(job)
                .step(step)
                .sharedState(sharedState)
                .build();

        Map<String, Object> resolved = PipelineVariableResolver.resolveStepWith(ctx);

        assertEquals("registry.example.com/ns/demo:abc123", resolved.get("image"));
        @SuppressWarnings("unchecked")
        Map<String, Object> env = (Map<String, Object>) resolved.get("env");
        assertEquals("800", env.get("RUN_ID"));
        assertEquals("build_job", env.get("JOB_ID"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> variables = (List<Map<String, Object>>) resolved.get("variables");
        assertEquals("v1.0.0", variables.get(0).get("value"));
    }

}
