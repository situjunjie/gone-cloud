package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineExecutionContextTest {

    @Test
    void localCheckoutDoesNotSatisfyPodCheckout() {
        PipelineExecutionContext local = PipelineExecutionContext.builder().build();
        local.setSourcePrepared(true);
        local.setSourcePreparedByCodeMerge(true);
        PipelineExecutionContext pod = podContext(local, "pod-1", "SHARED_PVC");
        assertFalse(pod.isSourcePrepared());
        assertFalse(pod.isSourcePreparedByCodeMerge());
        assertTrue(local.isSourcePrepared());
    }

    @Test
    void emptyDirCheckoutIsScopedToPodAndSurvivesStepDerivation() {
        PipelineExecutionContext run = PipelineExecutionContext.builder().build();
        PipelineExecutionContext first = podContext(run, "pod-1", "EMPTY_DIR");
        first.setSourcePrepared(true);
        first.setSourcePreparedByCodeMerge(true);
        assertTrue(first.forStep(null).isSourcePrepared());
        assertTrue(first.forStep(null).isSourcePreparedByCodeMerge());
        assertFalse(podContext(run, "pod-2", "EMPTY_DIR").isSourcePrepared());
        assertFalse(podContext(run, "pod-2", "EMPTY_DIR").isSourcePreparedByCodeMerge());
        assertFalse(run.isSourcePrepared());
    }

    @Test
    void persistentCheckoutAndMergeAreSharedWithinRun() {
        for (String storage : new String[]{"SHARED_PVC", "DYNAMIC_PVC"}) {
            PipelineExecutionContext run = PipelineExecutionContext.builder().build();
            PipelineExecutionContext first = podContext(run, "pod-1", storage);
            first.setSourcePrepared(true);
            first.setSourcePreparedByCodeMerge(true);
            PipelineExecutionContext next = podContext(run, "pod-2", storage);
            assertTrue(next.isSourcePrepared());
            assertTrue(next.isSourcePreparedByCodeMerge());
            assertFalse(run.isSourcePrepared());
        }
    }

    private PipelineExecutionContext podContext(PipelineExecutionContext run, String pod, String storage) {
        return run.toBuilder().executorRuntime(PipelineJobRuntime.builder()
                .runtimeType("KUBERNETES").runtimeId(pod)
                .metadata(Map.of("workspaceStorageType", storage, "environmentId", 1L, "namespace", "builds"))
                .build()).build();
    }
}
