package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_CACHE_CLEAR_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_CACHE_CLEAR_PATH_INVALID;

/**
 * 本机流水线任务工作目录服务。
 */
@Component
public class LocalPipelineWorkspaceService implements PipelineWorkspaceService {

    private static final String CACHE_METADATA_FILE = "cache-directories.json";
    private static final String RUN_WORKSPACE_OWNER_FILE = ".gone-devops/run-owner";

    private final Map<String, Object> runWorkspaceLocks = new ConcurrentHashMap<>();

    @Value("${yudao.devops.pipeline.workspace-root:${java.io.tmpdir}/gone-devops/pipeline-workspaces}")
    private String workspaceRoot;

    @Resource
    private PipelineCacheConfigResolver pipelineCacheConfigResolver;

    @Override
    public PipelineWorkspace createWorkspace(PipelineRunDO run, PipelineSpec.ExecutableJob job, PipelineCacheConfig cacheConfig) {
        Path runWorkspace = buildRunWorkspace(run);
        Path cacheWorkspace = buildCacheWorkspace(run, runWorkspace);
        List<PipelineCacheMount> cacheMounts = pipelineCacheConfigResolver.enabledDirectories(cacheConfig).stream()
                .map(directory -> PipelineCacheMount.builder()
                        .id(directory.getId())
                        .containerPath(directory.getPath())
                        .hostPath(resolveCachePath(cacheWorkspace, directory.getPath()))
                        .description(directory.getDescription())
                        .build())
                .toList();
        Object lock = runWorkspaceLocks.computeIfAbsent(runWorkspace.toString(), key -> new Object());
        try {
            synchronized (lock) {
                prepareRunWorkspace(run, runWorkspace, job);
                Files.createDirectories(cacheWorkspace);
                for (PipelineCacheMount cacheMount : cacheMounts) {
                    Files.createDirectories(cacheMount.getHostPath());
                }
                recordCachePaths(cacheWorkspace, cacheMounts.stream().map(PipelineCacheMount::getContainerPath).toList());
            }
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
    public Path getDefinitionCacheRoot(PipelineDefinitionDO definition) {
        return definitionRoot(definition.getTenantId(), definition.getAppId(),
                definition.getApplicationEnvId(), definition.getId()).resolve("cache").normalize();
    }

    @Override
    public void clearDefinitionCache(PipelineDefinitionDO definition, Collection<String> containerPaths) {
        if (CollUtil.isEmpty(containerPaths)) {
            return;
        }
        Path cacheRoot = getDefinitionCacheRoot(definition);
        for (String containerPath : containerPaths) {
            Path cachePath = resolveCachePath(cacheRoot, containerPath);
            if (!cachePath.normalize().startsWith(cacheRoot.normalize())) {
                throw exception(PIPELINE_CACHE_CLEAR_PATH_INVALID, containerPath);
            }
            try {
                clearPath(cachePath);
            } catch (IOException ex) {
                throw exception(PIPELINE_CACHE_CLEAR_FAIL, containerPath + ": " + ex.getMessage());
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
        Path ownerFile = runWorkspace.resolve(RUN_WORKSPACE_OWNER_FILE).normalize();
        String owner = buildRunWorkspaceOwner(run);
        if (!owner.equals(readRunWorkspaceOwner(ownerFile))) {
            clearPath(runWorkspace);
        }
        Files.createDirectories(runWorkspace);
        createRunWorkspaceDirectories(runWorkspace, job);
        writeRunWorkspaceOwner(ownerFile, owner);
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

    private String readRunWorkspaceOwner(Path ownerFile) throws IOException {
        if (!Files.exists(ownerFile)) {
            return null;
        }
        return Files.readString(ownerFile).trim();
    }

    private void writeRunWorkspaceOwner(Path ownerFile, String owner) throws IOException {
        Path metadataDir = ownerFile.getParent();
        if (metadataDir != null) {
            Files.createDirectories(metadataDir);
        }
        Files.writeString(ownerFile, owner);
    }

    private Path buildCacheWorkspace(PipelineRunDO run, Path runWorkspace) {
        if (isDefinitionScoped(run)) {
            return runRoot(run).resolve("cache").normalize();
        }
        return runWorkspace.getParent().resolve("run-" + run.getId() + "-cache").normalize();
    }

    private Path runRoot(PipelineRunDO run) {
        if (isDefinitionScoped(run)) {
            return definitionRoot(run.getTenantId(), run.getAppId(), run.getApplicationEnvId(), run.getDefinitionId());
        }
        return Path.of(workspaceRoot, "tenants", "tenant-" + safeId(run.getTenantId()), "standalone",
                "definition-" + safeId(run.getDefinitionId())).normalize();
    }

    private Path definitionRoot(Long tenantId, Long appId, Long applicationEnvId, Long definitionId) {
        return Path.of(workspaceRoot, "tenants", "tenant-" + safeId(tenantId), "apps", "app-" + safeId(appId),
                "envs", "app-env-" + safeId(applicationEnvId), "pipelines",
                "definition-" + safeId(definitionId)).normalize();
    }

    private boolean isDefinitionScoped(PipelineRunDO run) {
        return run.getAppId() != null && run.getApplicationEnvId() != null && run.getDefinitionId() != null;
    }

    private Path resolveCachePath(Path cacheRoot, String containerPath) {
        return cacheRoot.resolve("by-path").resolve(pipelineCacheConfigResolver.pathHash(containerPath)).normalize();
    }

    private void recordCachePaths(Path cacheWorkspace, List<String> containerPaths) throws IOException {
        Path metadataDir = cacheWorkspace.resolve("metadata");
        Files.createDirectories(metadataDir);
        Path metadataFile = metadataDir.resolve(CACHE_METADATA_FILE);
        Set<String> paths = new LinkedHashSet<>(containerPaths);
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
        return (isDefinitionScoped(run) ? "definition-" + run.getDefinitionId() : "run-" + run.getId());
    }

    private String buildRunWorkspaceOwner(PipelineRunDO run) {
        return "run-" + safeId(run.getId());
    }

    private String safeId(Long id) {
        return id == null ? "unknown" : String.valueOf(id);
    }

}
