package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        assertTrue(Files.exists(workspace.getRunWorkspace()),
                "run workspace 根目录应在 reset 后可继续使用");
        assertTrue(Files.notExists(source),
                "run workspace 目录内容应被清空");
        assertTrue(Files.exists(cacheMarker),
                "缓存目录是 run workspace 的兄弟目录,不应被清理");
    }

    @Test
    void testResetRunWorkspace_preservesGitObjectStoreForCrossRunReuse() {
        LocalPipelineWorkspaceService service = buildService();
        PipelineRunDO run = run(800L);
        PipelineWorkspace workspace = service.createWorkspace(run, job("build"), cacheConfig());
        Path gitDir = workspace.getRunWorkspace().resolve(".git");
        writeString(gitDir.resolve("HEAD"), "ref: refs/heads/master");
        Path packedObjects = gitDir.resolve("objects").resolve("pack").resolve("pack-1.pack");
        writeString(packedObjects, "objects");
        // 上一轮构建遗留的未跟踪产物
        writeString(workspace.getRunWorkspace().resolve("target").resolve("app.jar"), "stale");
        Path platformArtifact = workspace.getRunWorkspace().resolve("artifacts").resolve("old.txt");
        writeString(platformArtifact, "old");

        service.resetRunWorkspace(run);

        assertTrue(Files.exists(gitDir.resolve("HEAD")),
                ".git 对象库必须保留，否则无法跨运行复用已下载的 Git 历史");
        assertTrue(Files.exists(packedObjects), ".git 下的 pack 文件必须保留");
        assertTrue(Files.notExists(workspace.getRunWorkspace().resolve("target")),
                "上一轮构建产物必须清除，避免污染下一轮构建");
        assertTrue(Files.notExists(platformArtifact), "平台目录内容应被清空");
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
        RLock lock = mock(RLock.class);
        try {
            when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        } catch (InterruptedException ignored) {
            // tryLock 的受检异常仅来自接口声明，mock 不会真正抛出。
        }
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        RedissonClient redissonClient = mock(RedissonClient.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
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
