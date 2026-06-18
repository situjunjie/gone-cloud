package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PipelineCacheConfigResolver} 的单元测试。
 */
class PipelineCacheConfigResolverTest {

    private final PipelineCacheConfigResolver resolver = new PipelineCacheConfigResolver();

    @Test
    void testDefaultConfig_containsBuildToolCaches() {
        PipelineCacheConfig config = resolver.defaultConfig();

        List<String> paths = config.getDirectories().stream()
                .map(PipelineCacheConfig.Directory::getPath)
                .toList();
        assertTrue(paths.contains("/root/.m2"));
        assertTrue(paths.contains("/root/.npm"));
        assertTrue(paths.contains("/root/.pnpm-store"));
        assertTrue(paths.contains("/root/.gradle/caches"));
    }

    @Test
    void testEnabledDirectories_disabledIgnored() {
        PipelineCacheConfig config = new PipelineCacheConfig();
        config.setDirectories(List.of(directory("maven", "/root/.m2", true),
                directory("npm", "/root/.npm", false)));

        List<PipelineCacheConfig.Directory> directories = resolver.enabledDirectories(config);

        assertEquals(1, directories.size());
        assertEquals("/root/.m2", directories.get(0).getPath());
    }

    @Test
    void testNormalizeOrDefault_rejectsWorkspacePath() {
        PipelineCacheConfig config = new PipelineCacheConfig();
        config.setDirectories(List.of(directory("source", "/workspace/.m2", true)));

        assertThrows(ServiceException.class, () -> resolver.normalizeOrDefault(config));
    }

    @Test
    void testNormalizeOrDefault_rejectsNestedEnabledPaths() {
        PipelineCacheConfig config = new PipelineCacheConfig();
        config.setDirectories(List.of(directory("root", "/root/.cache", true),
                directory("child", "/root/.cache/pip", true)));

        assertThrows(ServiceException.class, () -> resolver.normalizeOrDefault(config));
    }

    @Test
    void testResolveVersionConfig_invalidStoredConfigFallbackToDefault() {
        PipelineCacheConfig config = resolver.resolveVersionConfig("""
                {"directories":[{"id":"source","path":"/workspace/.m2","enabled":true}]}
                """);

        assertEquals("/root/.m2", config.getDirectories().get(0).getPath());
    }

    private PipelineCacheConfig.Directory directory(String id, String path, Boolean enabled) {
        PipelineCacheConfig.Directory directory = new PipelineCacheConfig.Directory();
        directory.setId(id);
        directory.setPath(path);
        directory.setEnabled(enabled);
        return directory;
    }

}
