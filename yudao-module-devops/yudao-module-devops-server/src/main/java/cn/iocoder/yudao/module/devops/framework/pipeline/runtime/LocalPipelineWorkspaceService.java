package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionDO;
import cn.iocoder.yudao.module.devops.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_CACHE_CLEAR_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_CACHE_CLEAR_PATH_INVALID;

/**
 * 本机流水线任务工作目录服务。
 */
@Component
public class LocalPipelineWorkspaceService implements PipelineWorkspaceService {

    private static final String CACHE_METADATA_FILE = "cache-directories.json";

    /** 等待获取工作目录锁的上限：网络存储下并发创建给足余地。 */
    private static final long LOCK_WAIT_MS = 30_000L;
    /** 持锁上限：lease 自动释放兜底，避免节点宕机导致死锁。 */
    private static final long LOCK_LEASE_MS = 60_000L;

    @Value("${yudao.devops.pipeline.workspace-root:${java.io.tmpdir}/gone-devops/pipeline-workspaces}")
    private String workspaceRoot;

    @Resource
    private PipelineCacheConfigResolver pipelineCacheConfigResolver;

    @Resource
    private RedissonClient redissonClient;

    @Override
    public PipelineWorkspace createWorkspace(PipelineRunDO run, PipelineCacheConfig cacheConfig) {
        return createWorkspace(run, null, cacheConfig);
    }

    @Override
    public PipelineWorkspace createWorkspace(PipelineRunDO run, PipelineSpec.ExecutableJob job, PipelineCacheConfig cacheConfig) {
        Path runWorkspace = buildRunWorkspace(run);
        Path cacheWorkspace = buildCacheWorkspace(run);
        List<PipelineCacheMount> cacheMounts = pipelineCacheConfigResolver.enabledDirectories(cacheConfig).stream()
                .map(directory -> {
                    String canonicalPath = directory.getPath();
                    return PipelineCacheMount.builder()
                            .id(directory.getId())
                            .canonicalPath(canonicalPath)
                            .containerPath(pipelineCacheConfigResolver.resolveContainerPath(canonicalPath))
                            .hostPath(resolveCachePath(cacheWorkspace, canonicalPath))
                            .description(directory.getDescription())
                            .build();
                })
                .toList();
        try {
            runWithWorkspaceLock(runWorkspace, () -> {
                prepareRunWorkspace(run, runWorkspace, job);
                Files.createDirectories(cacheWorkspace);
                for (PipelineCacheMount cacheMount : cacheMounts) {
                    Files.createDirectories(cacheMount.getHostPath());
                }
                recordCachePaths(cacheWorkspace, cacheMounts.stream().map(PipelineCacheMount::getCanonicalPath).toList());
            });
            return PipelineWorkspace.builder()
                    .runWorkspace(runWorkspace)
                    .cacheWorkspace(cacheWorkspace)
                    .cacheKey(buildCacheKey(run))
                    .cacheMounts(cacheMounts)
                    .build();
        } catch (IOException ex) {
            throw new IllegalStateException("Create workspace failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void resetRunWorkspace(PipelineRunDO run) {
        Path runWorkspace = buildRunWorkspace(run);
        if (!Files.exists(runWorkspace)) {
            return;
        }
        try {
            runWithWorkspaceLock(runWorkspace, () -> {
                clearPath(runWorkspace);
                Files.createDirectories(runWorkspace);
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Reset run workspace failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Path getDefinitionCacheRoot(PipelineDefinitionDO definition) {
        return definitionRoot(definition.getTenantId(), definition.getAppId(),
                definition.getApplicationEnvId(), definition.getId()).resolve("cache").normalize();
    }

    @Override
    public void clearDefinitionCache(PipelineDefinitionDO definition, Collection<String> cachePaths) {
        if (CollUtil.isEmpty(cachePaths)) {
            return;
        }
        Path cacheRoot = getDefinitionCacheRoot(definition);
        for (String cachePathValue : cachePaths) {
            Path cachePath = resolveCachePath(cacheRoot, cachePathValue);
            if (!cachePath.normalize().startsWith(cacheRoot.normalize())) {
                throw exception(PIPELINE_CACHE_CLEAR_PATH_INVALID, cachePathValue);
            }
            try {
                clearPath(cachePath);
            } catch (IOException ex) {
                throw exception(PIPELINE_CACHE_CLEAR_FAIL, cachePathValue + ": " + ex.getMessage());
            }
        }
    }

    @Override
    public Set<String> listRecordedCachePaths(PipelineDefinitionDO definition) {
        Path metadataPath = getDefinitionCacheRoot(definition).resolve("metadata").resolve(CACHE_METADATA_FILE);
        if (!Files.exists(metadataPath)) {
            return Set.of();
        }
        try {
            List<String> paths = JsonUtils.parseObject(Files.readString(metadataPath), new TypeReference<List<String>>() {});
            return paths == null ? Set.of() : new LinkedHashSet<>(paths);
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private Path buildRunWorkspace(PipelineRunDO run) {
        return runRoot(run).resolve("workspace").normalize();
    }

    private void prepareRunWorkspace(PipelineRunDO run, Path runWorkspace, PipelineSpec.ExecutableJob job) throws IOException {
        Files.createDirectories(runWorkspace);
        createRunWorkspaceDirectories(runWorkspace, job);
    }

    private void createRunWorkspaceDirectories(Path runWorkspace, PipelineSpec.ExecutableJob job) throws IOException {
        Files.createDirectories(runWorkspace.resolve("artifacts"));
        Files.createDirectories(runWorkspace.resolve("reports"));
        Files.createDirectories(runWorkspace.resolve("tmp"));
        if (job == null || StrUtil.isBlank(job.getJobId())) {
            return;
        }
        Path jobWorkspace = runWorkspace.resolve("jobs").resolve(job.getJobId()).normalize();
        if (!jobWorkspace.startsWith(runWorkspace.resolve("jobs").normalize())) {
            return;
        }
        Files.createDirectories(jobWorkspace.resolve("artifacts"));
        Files.createDirectories(jobWorkspace.resolve("reports"));
        Files.createDirectories(jobWorkspace.resolve("tmp"));
    }

    private Path buildCacheWorkspace(PipelineRunDO run) {
        return runRoot(run).resolve("cache").normalize();
    }

    private Path runRoot(PipelineRunDO run) {
        return definitionRoot(run.getTenantId(), run.getAppId(), run.getApplicationEnvId(), run.getDefinitionId());
    }

    private Path definitionRoot(Long tenantId, Long appId, Long applicationEnvId, Long definitionId) {
        return Path.of(workspaceRoot, "tenants", "tenant-" + safeId(tenantId), "apps", "app-" + safeId(appId),
                "envs", "app-env-" + safeId(applicationEnvId), "pipelines",
                "definition-" + safeId(definitionId)).normalize();
    }

    private Path resolveCachePath(Path cacheRoot, String canonicalPath) {
        return cacheRoot.resolve("by-path").resolve(pipelineCacheConfigResolver.pathHash(canonicalPath)).normalize();
    }

    private void recordCachePaths(Path cacheWorkspace, List<String> cachePaths) throws IOException {
        Path metadataDir = cacheWorkspace.resolve("metadata");
        Files.createDirectories(metadataDir);
        Path metadataFile = metadataDir.resolve(CACHE_METADATA_FILE);
        Set<String> paths = new LinkedHashSet<>(cachePaths);
        if (Files.exists(metadataFile)) {
            try {
                List<String> existing = JsonUtils.parseObject(Files.readString(metadataFile), new TypeReference<List<String>>() {});
                if (existing != null) {
                    paths.addAll(existing);
                }
            } catch (Exception ignored) {
                // Rewrite invalid metadata from the current runtime config.
            }
        }
        Files.writeString(metadataFile, JsonUtils.toJsonString(paths));
    }

    private void clearPath(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(dir) && !dir.equals(path)) {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private String buildCacheKey(PipelineRunDO run) {
        return "definition-" + run.getDefinitionId();
    }

    private String safeId(Long id) {
        return id == null ? "unknown" : String.valueOf(id);
    }

    /**
     * 在工作目录分布式锁保护下执行操作。
     * <p>
     * 与原进程锁的差异：原 {@code synchronized} 无限等待，这里改为 {@link #LOCK_WAIT_MS} 上限等待，
     * 超时或中断都收敛为 {@link IllegalStateException}，避免跨节点不可见的死等。
     *
     * @param workspace 工作目录路径，作为锁 key（多节点共享存储需挂载到相同路径）
     * @param action    锁内执行的操作，允许抛出 {@link IOException} 由调用方原样转译
     */
    private void runWithWorkspaceLock(Path workspace, WorkspaceAction action) throws IOException {
        String lockKey = String.format(RedisKeyConstants.PIPELINE_WORKSPACE_LOCK, workspace.toString());
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_MS, LOCK_LEASE_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Acquire workspace lock interrupted: " + lockKey, ex);
        }
        if (!acquired) {
            throw new IllegalStateException("Acquire workspace lock timeout: " + lockKey);
        }
        try {
            action.run();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @FunctionalInterface
    private interface WorkspaceAction {

        void run() throws IOException;

    }

}
