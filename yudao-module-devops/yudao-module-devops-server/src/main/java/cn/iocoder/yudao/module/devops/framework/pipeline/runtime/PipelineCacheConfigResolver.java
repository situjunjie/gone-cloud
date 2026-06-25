package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_CACHE_CONFIG_INVALID;

/**
 * 流水线缓存配置解析器。
 */
@Component
public class PipelineCacheConfigResolver {

    private static final String SCHEMA_VERSION = "1.1";
    private static final int MAX_DIRECTORIES = 20;
    private static final int MAX_PATH_LENGTH = 255;
    private static final int MAX_ID_LENGTH = 64;
    private static final int MAX_DESCRIPTION_LENGTH = 128;

    /**
     * 工作目录变量名，解析为容器内 run workspace 挂载点。
     */
    private static final String WORKSPACE_VARIABLE = "WORKSPACE";
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private static final List<DefaultDirectory> DEFAULT_DIRECTORIES = List.of(
            new DefaultDirectory("maven", "/root/.m2", "Maven local repository"),
            new DefaultDirectory("gradle", "/root/.gradle/caches", "Gradle dependency cache"),
            new DefaultDirectory("npm", "/root/.npm", "npm package cache"),
            new DefaultDirectory("pnpm", "/root/.pnpm-store", "pnpm store cache"),
            new DefaultDirectory("yarn", "/root/.yarn", "Yarn cache"),
            new DefaultDirectory("go-mod", "/go/pkg/mod", "Go module cache"),
            new DefaultDirectory("user-cache", "/root/.cache", "Generic user cache")
    );
    private static final List<String> FORBIDDEN_PREFIXES = List.of("/workspace", "/proc", "/sys", "/dev", "/run", "/tmp");

    /**
     * 解析并规范化缓存配置；空配置返回默认配置。
     *
     * @param config 缓存配置
     * @return 规范化后的配置
     */
    public PipelineCacheConfig normalizeOrDefault(PipelineCacheConfig config) {
        if (config == null || CollUtil.isEmpty(config.getDirectories())) {
            return defaultConfig();
        }
        if (config.getDirectories().size() > MAX_DIRECTORIES) {
            throw exception(PIPELINE_CACHE_CONFIG_INVALID, "缓存目录数量不能超过 " + MAX_DIRECTORIES);
        }
        PipelineCacheConfig normalized = new PipelineCacheConfig();
        normalized.setSchemaVersion(StrUtil.blankToDefault(config.getSchemaVersion(), SCHEMA_VERSION));
        Map<String, PipelineCacheConfig.Directory> directoryMap = new LinkedHashMap<>();
        for (PipelineCacheConfig.Directory directory : config.getDirectories()) {
            PipelineCacheConfig.Directory item = normalizeDirectory(directory);
            // 以解析后的容器路径作为去重键，避免不同写法解析到同一挂载点
            String containerPath = resolveContainerPath(item.getPath());
            if (directoryMap.containsKey(containerPath)) {
                throw exception(PIPELINE_CACHE_CONFIG_INVALID, "缓存目录重复：" + item.getPath());
            }
            directoryMap.put(containerPath, item);
        }
        rejectNestedEnabledPaths(directoryMap.values().stream().toList());
        normalized.setDirectories(new ArrayList<>(directoryMap.values()));
        return normalized;
    }

    /**
     * 解析并规范化缓存配置 JSON；空或非法配置返回默认配置。
     *
     * @param configJson 缓存配置 JSON
     * @return 规范化后的配置
     */
    public PipelineCacheConfig resolveVersionConfig(String configJson) {
        if (StrUtil.isBlank(configJson)) {
            return defaultConfig();
        }
        try {
            return normalizeOrDefault(JsonUtils.parseObject(configJson, PipelineCacheConfig.class));
        } catch (Exception ignored) {
            return defaultConfig();
        }
    }

    /**
     * 返回默认缓存配置。
     *
     * @return 默认缓存配置
     */
    public PipelineCacheConfig defaultConfig() {
        PipelineCacheConfig config = new PipelineCacheConfig();
        config.setSchemaVersion(SCHEMA_VERSION);
        List<PipelineCacheConfig.Directory> directories = new ArrayList<>();
        for (DefaultDirectory item : DEFAULT_DIRECTORIES) {
            PipelineCacheConfig.Directory directory = new PipelineCacheConfig.Directory();
            directory.setId(item.id());
            directory.setPath(item.path());
            directory.setDescription(item.description());
            directory.setEnabled(true);
            directories.add(directory);
        }
        config.setDirectories(directories);
        return config;
    }

    /**
     * 获取启用的缓存目录。
     *
     * @param config 缓存配置
     * @return 启用的缓存目录
     */
    public List<PipelineCacheConfig.Directory> enabledDirectories(PipelineCacheConfig config) {
        return normalizeOrDefault(config).getDirectories().stream()
                .filter(directory -> !Boolean.FALSE.equals(directory.getEnabled()))
                .toList();
    }

    /**
     * 计算容器路径对应的稳定哈希。
     *
     * @param containerPath 容器路径
     * @return SHA-256 十六进制字符串
     */
    public String pathHash(String containerPath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(containerPath.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Create cache path hash failed", ex);
        }
    }

    /**
     * 将规范化缓存路径中的变量解析为容器内实际路径。
     *
     * <p>当前仅支持开头的 {@code ${WORKSPACE}}，解析为 run workspace 容器挂载点；
     * 不含变量的路径原样返回。
     *
     * @param canonicalPath 规范化的缓存路径（可能含变量）
     * @return 容器内实际路径
     */
    public String resolveContainerPath(String canonicalPath) {
        if (StrUtil.isBlank(canonicalPath) || !canonicalPath.startsWith("${")) {
            return canonicalPath;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(canonicalPath);
        if (matcher.lookingAt() && WORKSPACE_VARIABLE.equals(matcher.group(1))) {
            return PipelineWorkspaceConstants.CONTAINER_WORKSPACE + canonicalPath.substring(matcher.end());
        }
        return canonicalPath;
    }

    private PipelineCacheConfig.Directory normalizeDirectory(PipelineCacheConfig.Directory directory) {
        if (directory == null) {
            throw exception(PIPELINE_CACHE_CONFIG_INVALID, "缓存目录不能为空");
        }
        String path = normalizePath(directory.getPath());
        validatePath(path);
        PipelineCacheConfig.Directory normalized = new PipelineCacheConfig.Directory();
        normalized.setPath(path);
        normalized.setId(normalizeId(directory.getId(), path));
        normalized.setDescription(StrUtil.subPre(StrUtil.trim(directory.getDescription()), MAX_DESCRIPTION_LENGTH));
        normalized.setEnabled(!Boolean.FALSE.equals(directory.getEnabled()));
        return normalized;
    }

    private String normalizePath(String path) {
        String normalized = StrUtil.trim(path);
        if (StrUtil.isBlank(normalized)) {
            throw exception(PIPELINE_CACHE_CONFIG_INVALID, "缓存目录路径不能为空");
        }
        normalized = normalized.replaceAll("/{2,}", "/");
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private void validatePath(String path) {
        if (path.length() > MAX_PATH_LENGTH) {
            throw exception(PIPELINE_CACHE_CONFIG_INVALID, "缓存目录路径过长：" + path);
        }
        rejectPathTraversal(path);
        if (path.startsWith("${")) {
            validateVariablePath(path);
            return;
        }
        validateAbsolutePath(path);
    }

    private void validateVariablePath(String path) {
        Matcher matcher = VARIABLE_PATTERN.matcher(path);
        if (!matcher.lookingAt()) {
            throw exception(PIPELINE_CACHE_CONFIG_INVALID, "缓存目录变量格式无效：" + path);
        }
        String variable = matcher.group(1);
        if (!WORKSPACE_VARIABLE.equals(variable)) {
            throw exception(PIPELINE_CACHE_CONFIG_INVALID, "未知缓存变量：${" + variable + "}");
        }
        String remainder = path.substring(matcher.end());
        if (StrUtil.isBlank(remainder) || !remainder.startsWith("/") || "/".equals(remainder)) {
            throw exception(PIPELINE_CACHE_CONFIG_INVALID, "缓存目录不能是整个工作目录：" + path);
        }
        // 变量后续部分不允许再嵌套变量
        if (VARIABLE_PATTERN.matcher(remainder).find()) {
            throw exception(PIPELINE_CACHE_CONFIG_INVALID, "缓存目录变量只能位于开头：" + path);
        }
        // 解析后的容器路径仍需通过危险前缀校验（${WORKSPACE} 自身的 /workspace 前缀除外）
        String resolved = resolveContainerPath(path);
        rejectPathTraversal(resolved);
        for (String prefix : FORBIDDEN_PREFIXES) {
            if (PipelineWorkspaceConstants.CONTAINER_WORKSPACE.equals(prefix)) {
                continue;
            }
            if (resolved.equals(prefix) || resolved.startsWith(prefix + "/")) {
                throw exception(PIPELINE_CACHE_CONFIG_INVALID, "缓存目录不允许挂载：" + resolved);
            }
        }
    }

    private void validateAbsolutePath(String path) {
        if (VARIABLE_PATTERN.matcher(path).find()) {
            throw exception(PIPELINE_CACHE_CONFIG_INVALID, "缓存目录变量只能位于开头：" + path);
        }
        if (!path.startsWith("/")) {
            throw exception(PIPELINE_CACHE_CONFIG_INVALID, "缓存目录必须是绝对路径：" + path);
        }
        if ("/".equals(path)) {
            throw exception(PIPELINE_CACHE_CONFIG_INVALID, "缓存目录不能是根目录");
        }
        rejectForbiddenPrefix(path);
    }

    private void rejectPathTraversal(String path) {
        if (path.contains("/../") || path.endsWith("/..") || path.contains("/./") || path.endsWith("/.")) {
            throw exception(PIPELINE_CACHE_CONFIG_INVALID, "缓存目录不能包含相对路径：" + path);
        }
    }

    private void rejectForbiddenPrefix(String path) {
        for (String prefix : FORBIDDEN_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                throw exception(PIPELINE_CACHE_CONFIG_INVALID, "缓存目录不允许挂载：" + path);
            }
        }
    }

    private String normalizeId(String id, String path) {
        String normalized = StrUtil.blankToDefault(StrUtil.trim(id), "cache-" + pathHash(path).substring(0, 12));
        if (normalized.length() > MAX_ID_LENGTH || !normalized.matches("[a-zA-Z0-9._-]+")) {
            throw exception(PIPELINE_CACHE_CONFIG_INVALID, "缓存目录标识无效：" + normalized);
        }
        return normalized;
    }

    private void rejectNestedEnabledPaths(List<PipelineCacheConfig.Directory> directories) {
        Set<String> enabledPaths = new HashSet<>();
        for (PipelineCacheConfig.Directory directory : directories) {
            if (!Boolean.FALSE.equals(directory.getEnabled())) {
                // 以解析后的容器路径判断嵌套，覆盖 ${WORKSPACE} 变量场景
                enabledPaths.add(resolveContainerPath(directory.getPath()));
            }
        }
        for (String left : enabledPaths) {
            for (String right : enabledPaths) {
                if (!left.equals(right) && right.startsWith(left + "/")) {
                    throw exception(PIPELINE_CACHE_CONFIG_INVALID, "缓存目录不能嵌套：" + left + " 和 " + right);
                }
            }
        }
    }

    private record DefaultDirectory(String id, String path, String description) {
    }

}
