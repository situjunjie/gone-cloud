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
        Path marker = first.getRunWorkspace().resolve("repo-source.txt");
        writeString(marker, "keep-for-same-run");
        PipelineWorkspace second = service.createWorkspace(run, job("deploy"), cacheConfig());

        assertEquals(first.getRunWorkspace(), second.getRunWorkspace());
        assertTrue(first.getRunWorkspace().endsWith(Path.of("definition-30", "workspace")));
        assertTrue(Files.exists(first.getRunWorkspace().resolve("artifacts")));
        assertTrue(Files.exists(first.getRunWorkspace().resolve("jobs").resolve("build").resolve("tmp")));
        assertTrue(Files.exists(second.getRunWorkspace().resolve("jobs").resolve("deploy").resolve("tmp")));
        assertEquals("keep-for-same-run", readString(marker));
        assertEquals(1, first.getCacheMounts().size());
        PipelineCacheMount cacheMount = first.getCacheMounts().get(0);
        assertEquals("/root/.m2", cacheMount.getContainerPath());
        assertTrue(cacheMount.getHostPath().startsWith(first.getCacheWorkspace().resolve("by-path")));
        assertTrue(Files.exists(cacheMount.getHostPath()));
        assertEquals(Set.of("/root/.m2"), service.listRecordedCachePaths(definition()));
    }

    @Test
    void testCreateWorkspace_newDefinitionScopedRunReusesEnvironmentWorkspaceAndSharedCache() {
        LocalPipelineWorkspaceService service = buildService();

        PipelineWorkspace first = service.createWorkspace(run(800L), job("build"), cacheConfig());
        Path leftover = first.getRunWorkspace().resolve("jobs").resolve("build").resolve("tmp").resolve("stale.txt");
        writeString(leftover, "stale");
        PipelineWorkspace second = service.createWorkspace(run(801L), job("build"), cacheConfig());

        assertTrue(Files.exists(leftover));
        assertTrue(first.getRunWorkspace().endsWith(Path.of("definition-30", "workspace")));
        assertEquals(first.getRunWorkspace(), second.getRunWorkspace());
        assertEquals(first.getCacheWorkspace(), second.getCacheWorkspace());
    }

    @Test
    void testCreateWorkspace_standaloneRunStillUsesRunScopedWorkspace() {
        LocalPipelineWorkspaceService service = buildService();

        PipelineWorkspace first = service.createWorkspace(standaloneRun(800L), job("build"), cacheConfig());
        PipelineWorkspace second = service.createWorkspace(standaloneRun(801L), job("build"), cacheConfig());

        assertTrue(first.getRunWorkspace().endsWith(Path.of("runs", "run-800", "workspace")));
        assertTrue(second.getRunWorkspace().endsWith(Path.of("runs", "run-801", "workspace")));
        assertTrue(!first.getRunWorkspace().equals(second.getRunWorkspace()));
    }

    @Test
    void testClearDefinitionCache_deletesOnlyDerivedCachePath() throws Exception {
        LocalPipelineWorkspaceService service = buildService();
        PipelineWorkspace workspace = service.createWorkspace(run(800L), job("build"), cacheConfig());
        Path cachePath = workspace.getCacheMounts().get(0).getHostPath();
        Files.writeString(cachePath.resolve("marker.txt"), "cached");

        service.clearDefinitionCache(definition(), List.of("/root/.m2"));

        assertTrue(Files.notExists(cachePath));
        assertTrue(Files.exists(workspace.getRunWorkspace()));
    }

    @Test
    void testCreateWorkspace_workspaceRelativeCacheUsesCanonicalHashAndResolvedContainerPath() {
        LocalPipelineWorkspaceService service = buildService();

        PipelineWorkspace workspace = service.createWorkspace(run(800L), job("build"), workspaceRelativeCacheConfig());

        assertEquals(1, workspace.getCacheMounts().size());
        PipelineCacheMount cacheMount = workspace.getCacheMounts().get(0);
        assertEquals("${WORKSPACE}/node_modules", cacheMount.getCanonicalPath());
        assertEquals("/workspace/node_modules", cacheMount.getContainerPath());
        assertEquals(workspace.getCacheWorkspace().resolve("by-path")
                        .resolve(new PipelineCacheConfigResolver().pathHash("${WORKSPACE}/node_modules")),
                cacheMount.getHostPath());
        assertEquals(Set.of("${WORKSPACE}/node_modules"), service.listRecordedCachePaths(definition()));
    }

    @Test
    void testResetRunWorkspace_wipesWorkspaceContentsButPreservesSiblingCache() {
        LocalPipelineWorkspaceService service = buildService();
        PipelineRunDO run = run(800L);
        PipelineWorkspace workspace = service.createWorkspace(run, job("build"), cacheConfig());
        Path source = workspace.getRunWorkspace().resolve("src").resolve("App.java");
        writeString(source, "old-source");
        Path cacheMarker = workspace.getCacheMounts().get(0).getHostPath().resolve("marker.txt");
        writeString(cacheMarker, "cached");

        service.resetRunWorkspace(run);

        assertTrue(Files.notExists(workspace.getRunWorkspace()),
                "run workspace 目录内容应被清空");
        assertTrue(Files.exists(cacheMarker),
                "缓存目录是 run workspace 的兄弟目录,不应被清理");
    }

    @Test
    void testResetRunWorkspace_missingWorkspaceIsNoop() {
        LocalPipelineWorkspaceService service = buildService();

        service.resetRunWorkspace(run(800L));
    }

    private void writeString(Path path, String content) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String readString(Path path) {
        try {
            return Files.readString(path).trim();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
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

    private PipelineRunDO standaloneRun(Long id) {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(id);
        run.setTenantId(1L);
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

    private PipelineCacheConfig workspaceRelativeCacheConfig() {
        PipelineCacheConfig config = new PipelineCacheConfig();
        PipelineCacheConfig.Directory directory = new PipelineCacheConfig.Directory();
        directory.setId("node-modules");
        directory.setPath("${WORKSPACE}/node_modules");
        directory.setEnabled(true);
        config.setDirectories(List.of(directory));
        return config;
    }

}
