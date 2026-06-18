package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LocalPipelineWorkspaceService} 的单元测试。
 */
class LocalPipelineWorkspaceServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void testCreateWorkspace_runSharedAndCacheDerivedByPath() {
        LocalPipelineWorkspaceService service = buildService();
        PipelineRunDO run = run(800L);
        PipelineWorkspace first = service.createWorkspace(run, job("build"), cacheConfig());
        PipelineWorkspace second = service.createWorkspace(run, job("deploy"), cacheConfig());

        assertEquals(first.getRunWorkspace(), second.getRunWorkspace());
        assertTrue(first.getRunWorkspace().endsWith(Path.of("runs", "run-800")));
        assertTrue(Files.exists(first.getRunWorkspace().resolve("artifacts")));
        assertTrue(Files.exists(first.getRunWorkspace().resolve("jobs").resolve("build").resolve("tmp")));
        assertEquals(1, first.getCacheMounts().size());
        PipelineCacheMount cacheMount = first.getCacheMounts().get(0);
        assertEquals("/root/.m2", cacheMount.getContainerPath());
        assertTrue(cacheMount.getHostPath().startsWith(first.getCacheWorkspace().resolve("by-path")));
        assertTrue(Files.exists(cacheMount.getHostPath()));
        assertEquals(Set.of("/root/.m2"), service.listRecordedCachePaths(definition()));
    }

    @Test
    void testCreateWorkspace_differentRunsUseDifferentRunWorkspace() {
        LocalPipelineWorkspaceService service = buildService();

        PipelineWorkspace first = service.createWorkspace(run(800L), job("build"), cacheConfig());
        PipelineWorkspace second = service.createWorkspace(run(801L), job("build"), cacheConfig());

        assertFalse(first.getRunWorkspace().equals(second.getRunWorkspace()));
        assertEquals(first.getCacheWorkspace(), second.getCacheWorkspace());
    }

    @Test
    void testClearDefinitionCache_deletesOnlyDerivedCachePath() throws Exception {
        LocalPipelineWorkspaceService service = buildService();
        PipelineWorkspace workspace = service.createWorkspace(run(800L), job("build"), cacheConfig());
        Path cachePath = workspace.getCacheMounts().get(0).getHostPath();
        Files.writeString(cachePath.resolve("marker.txt"), "cached");

        service.clearDefinitionCache(definition(), List.of("/root/.m2"));

        assertFalse(Files.exists(cachePath));
        assertTrue(Files.exists(workspace.getRunWorkspace()));
    }

    private LocalPipelineWorkspaceService buildService() {
        LocalPipelineWorkspaceService service = new LocalPipelineWorkspaceService();
        ReflectionTestUtils.setField(service, "workspaceRoot", tempDir.toString());
        ReflectionTestUtils.setField(service, "pipelineCacheConfigResolver", new PipelineCacheConfigResolver());
        return service;
    }

    private PipelineRunDO run(Long id) {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(id);
        run.setTenantId(1L);
        run.setAppId(10L);
        run.setApplicationEnvId(20L);
        run.setDefinitionId(30L);
        return run;
    }

    private PipelineDefinitionDO definition() {
        PipelineDefinitionDO definition = new PipelineDefinitionDO();
        definition.setId(30L);
        definition.setTenantId(1L);
        definition.setAppId(10L);
        definition.setApplicationEnvId(20L);
        return definition;
    }

    private PipelineSpec.ExecutableJob job(String jobId) {
        PipelineSpec.ExecutableJob job = new PipelineSpec.ExecutableJob();
        job.setJobId(jobId);
        return job;
    }

    private PipelineCacheConfig cacheConfig() {
        PipelineCacheConfig config = new PipelineCacheConfig();
        PipelineCacheConfig.Directory directory = new PipelineCacheConfig.Directory();
        directory.setId("maven");
        directory.setPath("/root/.m2");
        directory.setEnabled(true);
        config.setDirectories(List.of(directory));
        return config;
    }

}
