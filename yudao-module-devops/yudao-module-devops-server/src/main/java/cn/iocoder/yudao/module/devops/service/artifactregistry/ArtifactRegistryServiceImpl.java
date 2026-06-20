package cn.iocoder.yudao.module.devops.service.artifactregistry;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactDockerSearchReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactMavenSearchReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactRegistryPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactRegistrySaveReqVO;
import cn.iocoder.yudao.module.devops.convert.artifactregistry.ArtifactRegistryConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry.ArtifactRegistryDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry.ArtifactRepositoryDO;
import cn.iocoder.yudao.module.devops.dal.mysql.artifactregistry.ArtifactRegistryMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.artifactregistry.ArtifactRepositoryMapper;
import cn.iocoder.yudao.module.devops.enums.ArtifactRegistryAuthTypeEnum;
import cn.iocoder.yudao.module.devops.enums.ArtifactRegistryCheckStatusEnum;
import cn.iocoder.yudao.module.devops.enums.ArtifactRegistryProviderTypeEnum;
import cn.iocoder.yudao.module.devops.enums.ArtifactRepositoryFormatEnum;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactDockerSearchReqDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactDockerSearchResultDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactMavenSearchReqDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactMavenSearchResultDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactRepositoryDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.*;

/**
 * DevOps 制品仓库 Service 实现类。
 */
@Service
@Validated
public class ArtifactRegistryServiceImpl implements ArtifactRegistryService {

    private static final Set<String> SUPPORTED_REPOSITORY_FORMATS = Set.of(
            ArtifactRepositoryFormatEnum.MAVEN2.getFormat(),
            ArtifactRepositoryFormatEnum.DOCKER.getFormat());

    @Resource
    private ArtifactRegistryMapper artifactRegistryMapper;
    @Resource
    private ArtifactRepositoryMapper artifactRepositoryMapper;
    @Resource
    private ArtifactRegistryClientFactory artifactRegistryClientFactory;

    @Override
    public Long createArtifactRegistry(ArtifactRegistrySaveReqVO createReqVO) {
        validateNameUnique(null, createReqVO.getName());
        validateRegistryConfig(createReqVO);
        if (StrUtil.isBlank(createReqVO.getPassword())) {
            throw exception(ARTIFACT_REGISTRY_PASSWORD_REQUIRED);
        }

        ArtifactRegistryDO registry = ArtifactRegistryConvert.INSTANCE.convert(createReqVO);
        registry.setServerUrl(normalizeUrl(registry.getServerUrl()));
        registry.setPasswordMask(maskPassword(registry.getPassword()));
        artifactRegistryMapper.insert(registry);
        return registry.getId();
    }

    @Override
    public void updateArtifactRegistry(ArtifactRegistrySaveReqVO updateReqVO) {
        ArtifactRegistryDO oldRegistry = validateArtifactRegistryExists(updateReqVO.getId());
        validateNameUnique(updateReqVO.getId(), updateReqVO.getName());
        validateRegistryConfig(updateReqVO);

        ArtifactRegistryDO updateObj = ArtifactRegistryConvert.INSTANCE.convert(updateReqVO);
        updateObj.setServerUrl(normalizeUrl(updateObj.getServerUrl()));
        if (StrUtil.isBlank(updateReqVO.getPassword())) {
            updateObj.setPassword(oldRegistry.getPassword());
            updateObj.setPasswordMask(oldRegistry.getPasswordMask());
        } else {
            updateObj.setPasswordMask(maskPassword(updateReqVO.getPassword()));
        }
        artifactRegistryMapper.updateById(updateObj);
    }

    @Override
    public void deleteArtifactRegistry(Long id) {
        validateArtifactRegistryExists(id);
        if (artifactRepositoryMapper.selectCountByRegistryId(id) > 0) {
            throw exception(ARTIFACT_REGISTRY_DELETE_FAIL_REPOSITORY_EXISTS);
        }
        artifactRegistryMapper.deleteById(id);
    }

    @Override
    public ArtifactRegistryDO getArtifactRegistry(Long id) {
        return artifactRegistryMapper.selectById(id);
    }

    @Override
    public PageResult<ArtifactRegistryDO> getArtifactRegistryPage(ArtifactRegistryPageReqVO pageReqVO) {
        return artifactRegistryMapper.selectPage(pageReqVO);
    }

    @Override
    public ArtifactRegistryDO validateArtifactRegistryExists(Long id) {
        ArtifactRegistryDO registry = artifactRegistryMapper.selectById(id);
        if (registry == null) {
            throw exception(ARTIFACT_REGISTRY_NOT_EXISTS);
        }
        return registry;
    }

    @Override
    public void checkArtifactRegistry(Long id) {
        ArtifactRegistryDO registry = validateArtifactRegistryExists(id);
        try {
            createClient(registry).checkConnection(registry);
            updateCheckResult(id, ArtifactRegistryCheckStatusEnum.SUCCESS.getStatus(), "连接成功");
        } catch (Exception ex) {
            String message = StrUtil.subPre(ex.getMessage(), 512);
            updateCheckResult(id, ArtifactRegistryCheckStatusEnum.FAIL.getStatus(), message);
            throw exception(ARTIFACT_REGISTRY_CONNECTION_FAIL, message);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ArtifactRepositoryDO> syncRepositories(Long registryId) {
        ArtifactRegistryDO registry = validateArtifactRegistryExists(registryId);
        List<ArtifactRepositoryDTO> repositories;
        try {
            repositories = createClient(registry).listRepositories(registry);
        } catch (Exception ex) {
            throw exception(ARTIFACT_REGISTRY_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
        LocalDateTime now = LocalDateTime.now();
        repositories.stream()
                .filter(repository -> SUPPORTED_REPOSITORY_FORMATS.contains(repository.getFormat()))
                .forEach(repository -> saveOrUpdateRepository(registryId, repository, now));
        return artifactRepositoryMapper.selectListByRegistryIdAndFormat(registryId, null);
    }

    @Override
    public List<ArtifactRepositoryDO> getRepositories(Long registryId, String format) {
        validateArtifactRegistryExists(registryId);
        format = StrUtil.blankToDefault(format, ArtifactRepositoryFormatEnum.MAVEN2.getFormat());
        if (StrUtil.isNotBlank(format) && !SUPPORTED_REPOSITORY_FORMATS.contains(format)) {
            throw exception(ARTIFACT_REPOSITORY_FORMAT_NOT_SUPPORTED);
        }
        return artifactRepositoryMapper.selectListByRegistryIdAndFormat(registryId, format);
    }

    @Override
    public ArtifactMavenSearchResultDTO searchMaven(ArtifactMavenSearchReqVO reqVO) {
        ArtifactRegistryDO registry = validateArtifactRegistryExists(reqVO.getRegistryId());
        ArtifactMavenSearchReqDTO reqDTO = convertSearchReq(reqVO);
        try {
            return createClient(registry).searchMaven(registry, reqDTO);
        } catch (Exception ex) {
            throw exception(ARTIFACT_SEARCH_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    @Override
    public ArtifactDockerSearchResultDTO searchDocker(ArtifactDockerSearchReqVO reqVO) {
        ArtifactRegistryDO registry = validateArtifactRegistryExists(reqVO.getRegistryId());
        ArtifactDockerSearchReqDTO reqDTO = convertDockerSearchReq(reqVO);
        try {
            return createClient(registry).searchDocker(registry, reqDTO);
        } catch (Exception ex) {
            throw exception(ARTIFACT_SEARCH_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    private void saveOrUpdateRepository(Long registryId, ArtifactRepositoryDTO repository, LocalDateTime syncTime) {
        ArtifactRepositoryDO oldRepository = artifactRepositoryMapper
                .selectByRegistryIdAndRepositoryName(registryId, repository.getRepositoryName());
        ArtifactRepositoryDO saveObj = new ArtifactRepositoryDO();
        saveObj.setRegistryId(registryId);
        saveObj.setRepositoryName(repository.getRepositoryName());
        saveObj.setFormat(repository.getFormat());
        saveObj.setRepositoryType(repository.getRepositoryType());
        saveObj.setUrl(repository.getUrl());
        saveObj.setOnline(repository.getOnline());
        saveObj.setStatus(CommonStatusEnum.ENABLE.getStatus());
        saveObj.setLastSyncTime(syncTime);
        if (oldRepository == null) {
            artifactRepositoryMapper.insert(saveObj);
        } else {
            saveObj.setId(oldRepository.getId());
            artifactRepositoryMapper.updateById(saveObj);
        }
    }

    private ArtifactMavenSearchReqDTO convertSearchReq(ArtifactMavenSearchReqVO reqVO) {
        ArtifactMavenSearchReqDTO reqDTO = new ArtifactMavenSearchReqDTO();
        reqDTO.setRepositoryName(resolveRepositoryName(reqVO.getRegistryId(), reqVO.getRepositoryId(),
                reqVO.getRepositoryName(), ArtifactRepositoryFormatEnum.MAVEN2.getFormat()));
        reqDTO.setKeyword(reqVO.getKeyword());
        reqDTO.setGroupId(reqVO.getGroupId());
        reqDTO.setArtifactId(reqVO.getArtifactId());
        reqDTO.setVersion(reqVO.getVersion());
        reqDTO.setContinuationToken(reqVO.getContinuationToken());
        reqDTO.setLimit(reqVO.getLimit());
        return reqDTO;
    }

    private ArtifactDockerSearchReqDTO convertDockerSearchReq(ArtifactDockerSearchReqVO reqVO) {
        ArtifactDockerSearchReqDTO reqDTO = new ArtifactDockerSearchReqDTO();
        reqDTO.setRepositoryName(resolveRepositoryName(reqVO.getRegistryId(), reqVO.getRepositoryId(),
                reqVO.getRepositoryName(), ArtifactRepositoryFormatEnum.DOCKER.getFormat()));
        reqDTO.setKeyword(reqVO.getKeyword());
        reqDTO.setImageName(reqVO.getImageName());
        reqDTO.setTag(reqVO.getTag());
        reqDTO.setContinuationToken(reqVO.getContinuationToken());
        return reqDTO;
    }

    private String resolveRepositoryName(Long registryId, Long repositoryId, String repositoryName, String requiredFormat) {
        if (repositoryId == null) {
            return repositoryName;
        }
        ArtifactRepositoryDO repository = artifactRepositoryMapper.selectById(repositoryId);
        if (repository == null) {
            throw exception(ARTIFACT_REPOSITORY_NOT_EXISTS);
        }
        if (!registryId.equals(repository.getRegistryId())) {
            throw exception(ARTIFACT_REPOSITORY_NOT_EXISTS);
        }
        if (!requiredFormat.equals(repository.getFormat())) {
            throw exception(ARTIFACT_REPOSITORY_FORMAT_NOT_SUPPORTED);
        }
        return repository.getRepositoryName();
    }

    private ArtifactRegistryClient createClient(ArtifactRegistryDO registry) {
        validateRegistryConfig(registry);
        return artifactRegistryClientFactory.getClient(registry);
    }

    private void validateNameUnique(Long id, String name) {
        ArtifactRegistryDO registry = artifactRegistryMapper.selectByName(name);
        if (registry != null && !registry.getId().equals(id)) {
            throw exception(ARTIFACT_REGISTRY_NAME_DUPLICATE);
        }
    }

    private void validateRegistryConfig(ArtifactRegistrySaveReqVO reqVO) {
        if (!ArtifactRegistryProviderTypeEnum.NEXUS3.getProviderType().equals(reqVO.getProviderType())) {
            throw exception(ARTIFACT_REGISTRY_TYPE_NOT_SUPPORTED);
        }
        if (!ArtifactRegistryAuthTypeEnum.USERNAME_PASSWORD.getAuthType().equals(reqVO.getAuthType())) {
            throw exception(ARTIFACT_REGISTRY_AUTH_TYPE_NOT_SUPPORTED);
        }
        if (StrUtil.isBlank(reqVO.getUsername())) {
            throw exception(ARTIFACT_REGISTRY_USERNAME_REQUIRED);
        }
    }

    private void validateRegistryConfig(ArtifactRegistryDO registry) {
        if (!ArtifactRegistryProviderTypeEnum.NEXUS3.getProviderType().equals(registry.getProviderType())) {
            throw exception(ARTIFACT_REGISTRY_TYPE_NOT_SUPPORTED);
        }
        if (!ArtifactRegistryAuthTypeEnum.USERNAME_PASSWORD.getAuthType().equals(registry.getAuthType())) {
            throw exception(ARTIFACT_REGISTRY_AUTH_TYPE_NOT_SUPPORTED);
        }
        if (StrUtil.isBlank(registry.getUsername())) {
            throw exception(ARTIFACT_REGISTRY_USERNAME_REQUIRED);
        }
        if (StrUtil.isBlank(registry.getPassword())) {
            throw exception(ARTIFACT_REGISTRY_PASSWORD_REQUIRED);
        }
    }

    private void updateCheckResult(Long id, Integer status, String message) {
        ArtifactRegistryDO updateObj = new ArtifactRegistryDO();
        updateObj.setId(id);
        updateObj.setLastCheckTime(LocalDateTime.now());
        updateObj.setLastCheckStatus(status);
        updateObj.setLastCheckMessage(message);
        artifactRegistryMapper.updateById(updateObj);
    }

    private String normalizeUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return url;
        }
        return StrUtil.removeSuffix(url.trim(), "/");
    }

    private String maskPassword(String password) {
        if (StrUtil.isBlank(password)) {
            return null;
        }
        String suffix = StrUtil.subSuf(password, Math.max(password.length() - 4, 0));
        return "****" + suffix;
    }

}
