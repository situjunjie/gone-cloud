package cn.iocoder.yudao.module.devops.service.artifactregistry;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry.ArtifactRegistryDO;
import cn.iocoder.yudao.module.devops.enums.ArtifactRepositoryFormatEnum;
import cn.iocoder.yudao.module.devops.enums.ArtifactRepositoryTypeEnum;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactDockerSearchReqDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactDockerSearchResultDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactMavenSearchReqDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactMavenSearchResultDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactRepositoryDTO;
import lombok.Data;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Nexus3 制品仓库客户端。
 */
public class NexusArtifactRegistryClient implements ArtifactRegistryClient {

    private static final int DEFAULT_TIMEOUT_MS = 10_000;

    @Override
    public void checkConnection(ArtifactRegistryDO registry) {
        requestGet(registry, "/service/rest/v1/repositories", Map.of());
    }

    @Override
    public List<ArtifactRepositoryDTO> listRepositories(ArtifactRegistryDO registry) {
        String body = requestGet(registry, "/service/rest/v1/repositories", Map.of());
        List<NexusRepositoryResp> repositories = JsonUtils.parseArray(body, NexusRepositoryResp.class);
        return repositories.stream()
                .map(this::convertRepository)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public ArtifactMavenSearchResultDTO searchMaven(ArtifactRegistryDO registry, ArtifactMavenSearchReqDTO reqDTO) {
        String body = requestGet(registry, "/service/rest/v1/search", buildMavenSearchParams(reqDTO));
        NexusSearchResp searchResp = JsonUtils.parseObject(body, NexusSearchResp.class);
        ArtifactMavenSearchResultDTO result = new ArtifactMavenSearchResultDTO();
        result.setContinuationToken(searchResp == null ? null : searchResp.getContinuationToken());
        List<NexusSearchItemResp> items = searchResp == null ? List.of() : searchResp.getItems();
        result.setItems(items == null ? List.of() : items.stream()
                .flatMap(item -> convertSearchItem(item).stream())
                .toList());
        return result;
    }

    @Override
    public ArtifactDockerSearchResultDTO searchDocker(ArtifactRegistryDO registry, ArtifactDockerSearchReqDTO reqDTO) {
        String body = requestGet(registry, "/service/rest/v1/search", buildDockerSearchParams(reqDTO));
        NexusSearchResp searchResp = JsonUtils.parseObject(body, NexusSearchResp.class);
        ArtifactDockerSearchResultDTO result = new ArtifactDockerSearchResultDTO();
        result.setContinuationToken(searchResp == null ? null : searchResp.getContinuationToken());
        List<NexusSearchItemResp> items = searchResp == null ? List.of() : searchResp.getItems();
        result.setItems(items == null ? List.of() : items.stream()
                .flatMap(item -> convertDockerSearchItem(item).stream())
                .toList());
        return result;
    }

    private ArtifactRepositoryDTO convertRepository(NexusRepositoryResp resp) {
        if (resp == null || StrUtil.isBlank(resp.getName()) || StrUtil.isBlank(resp.getType())) {
            return null;
        }
        ArtifactRepositoryFormatEnum format = ArtifactRepositoryFormatEnum.ofNexusFormat(resp.getFormat());
        if (format == null) {
            return null;
        }
        ArtifactRepositoryTypeEnum repositoryType = ArtifactRepositoryTypeEnum.ofNexusType(resp.getType());
        ArtifactRepositoryDTO dto = new ArtifactRepositoryDTO();
        dto.setRepositoryName(resp.getName());
        dto.setFormat(format.getFormat());
        dto.setRepositoryType(repositoryType == null ? resp.getType().toUpperCase(Locale.ROOT) : repositoryType.getRepositoryType());
        dto.setUrl(resp.getUrl());
        dto.setOnline(resp.getOnline());
        return dto;
    }

    private List<ArtifactMavenSearchResultDTO.Item> convertSearchItem(NexusSearchItemResp item) {
        if (item == null) {
            return List.of();
        }
        if (!ArtifactRepositoryFormatEnum.MAVEN2.getNexusFormat().equalsIgnoreCase(item.getFormat())) {
            return List.of();
        }
        if (CollUtil.isEmpty(item.getAssets())) {
            return List.of(convertSearchItemAsset(item, null));
        }
        return item.getAssets().stream()
                .map(asset -> convertSearchItemAsset(item, asset))
                .toList();
    }

    private ArtifactMavenSearchResultDTO.Item convertSearchItemAsset(NexusSearchItemResp item, NexusAssetResp asset) {
        NexusMavenResp maven = asset != null && asset.getMaven2() != null ? asset.getMaven2() : item.getMaven2();
        ArtifactMavenSearchResultDTO.Item result = new ArtifactMavenSearchResultDTO.Item();
        result.setRepository(item.getRepository());
        result.setGroupId(maven == null ? item.getGroup() : maven.getGroupId());
        result.setArtifactId(maven == null ? item.getName() : maven.getArtifactId());
        result.setVersion(item.getVersion());
        result.setBaseVersion(maven == null ? null : maven.getBaseVersion());
        result.setClassifier(maven == null ? null : maven.getClassifier());
        result.setExtension(maven == null ? null : maven.getExtension());
        if (asset != null) {
            result.setPath(asset.getPath());
            result.setDownloadUrl(asset.getDownloadUrl());
            result.setLastModified(parseDateTime(asset.getLastModified()));
        }
        return result;
    }

    private List<ArtifactDockerSearchResultDTO.Item> convertDockerSearchItem(NexusSearchItemResp item) {
        if (item == null) {
            return List.of();
        }
        if (!ArtifactRepositoryFormatEnum.DOCKER.getNexusFormat().equalsIgnoreCase(item.getFormat())) {
            return List.of();
        }
        if (CollUtil.isEmpty(item.getAssets())) {
            return List.of(convertDockerSearchItemAsset(item, null));
        }
        return item.getAssets().stream()
                .map(asset -> convertDockerSearchItemAsset(item, asset))
                .toList();
    }

    private ArtifactDockerSearchResultDTO.Item convertDockerSearchItemAsset(NexusSearchItemResp item, NexusAssetResp asset) {
        ArtifactDockerSearchResultDTO.Item result = new ArtifactDockerSearchResultDTO.Item();
        result.setRepository(item.getRepository());
        result.setImageName(item.getName());
        result.setTag(item.getVersion());
        if (asset != null) {
            result.setPath(asset.getPath());
            result.setDownloadUrl(asset.getDownloadUrl());
            result.setLastModified(parseDateTime(asset.getLastModified()));
        }
        return result;
    }

    private Map<String, Object> buildMavenSearchParams(ArtifactMavenSearchReqDTO reqDTO) {
        Map<String, Object> params = cn.hutool.core.map.MapUtil.newHashMap();
        putIfPresent(params, "repository", reqDTO.getRepositoryName());
        putIfPresent(params, "q", reqDTO.getKeyword());
        putIfPresent(params, "maven.groupId", reqDTO.getGroupId());
        putIfPresent(params, "maven.artifactId", reqDTO.getArtifactId());
        putIfPresent(params, "maven.baseVersion", reqDTO.getVersion());
        putIfPresent(params, "continuationToken", reqDTO.getContinuationToken());
        return params;
    }

    private Map<String, Object> buildDockerSearchParams(ArtifactDockerSearchReqDTO reqDTO) {
        Map<String, Object> params = cn.hutool.core.map.MapUtil.newHashMap();
        putIfPresent(params, "repository", reqDTO.getRepositoryName());
        putIfPresent(params, "q", reqDTO.getKeyword());
        putIfPresent(params, "docker.imageName", reqDTO.getImageName());
        putIfPresent(params, "docker.imageTag", reqDTO.getTag());
        putIfPresent(params, "continuationToken", reqDTO.getContinuationToken());
        return params;
    }

    private void putIfPresent(Map<String, Object> params, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String stringValue && StrUtil.isBlank(stringValue)) {
            return;
        }
        params.put(key, value);
    }

    private String requestGet(ArtifactRegistryDO registry, String path, Map<String, Object> queryParams) {
        String url = buildUrl(registry.getServerUrl(), path, queryParams);
        try (HttpResponse response = HttpRequest.get(url)
                .header("Authorization", buildAuthorization(registry))
                .timeout(DEFAULT_TIMEOUT_MS)
                .execute()) {
            if (!response.isOk()) {
                throw new IllegalStateException(StrUtil.format("HTTP {} {}", response.getStatus(), response.body()));
            }
            return response.body();
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private String buildUrl(String serverUrl, String path, Map<String, Object> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(StrUtil.removeSuffix(serverUrl.trim(), "/") + path);
        queryParams.forEach(builder::queryParam);
        return builder.build().encode().toUriString();
    }

    private String buildAuthorization(ArtifactRegistryDO registry) {
        String raw = registry.getUsername() + ":" + registry.getPassword();
        return "Basic " + Base64.encode(raw);
    }

    private LocalDateTime parseDateTime(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    @Data
    public static class NexusRepositoryResp {
        private String name;
        private String format;
        private String type;
        private String url;
        private Boolean online;
    }

    @Data
    public static class NexusSearchResp {
        private List<NexusSearchItemResp> items;
        private String continuationToken;
    }

    @Data
    public static class NexusSearchItemResp {
        private String id;
        private String repository;
        private String format;
        private String group;
        private String name;
        private String version;
        private NexusMavenResp maven2;
        private List<NexusAssetResp> assets;
    }

    @Data
    public static class NexusMavenResp {
        private String groupId;
        private String artifactId;
        private String version;
        private String baseVersion;
        private String classifier;
        private String extension;
    }

    @Data
    public static class NexusAssetResp {
        private String downloadUrl;
        private String path;
        private String lastModified;
        private NexusMavenResp maven2;
    }

}
