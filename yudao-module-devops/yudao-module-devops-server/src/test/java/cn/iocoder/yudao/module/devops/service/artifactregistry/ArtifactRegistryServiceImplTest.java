package cn.iocoder.yudao.module.devops.service.artifactregistry;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactDockerSearchReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactMavenSearchReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactRegistrySaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry.ArtifactRegistryDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry.ArtifactRepositoryDO;
import cn.iocoder.yudao.module.devops.dal.mysql.artifactregistry.ArtifactRegistryMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.artifactregistry.ArtifactRepositoryMapper;
import cn.iocoder.yudao.module.devops.enums.ArtifactRegistryAuthTypeEnum;
import cn.iocoder.yudao.module.devops.enums.ArtifactRegistryProviderTypeEnum;
import cn.iocoder.yudao.module.devops.enums.ArtifactRepositoryFormatEnum;
import cn.iocoder.yudao.module.devops.enums.ArtifactRepositoryTypeEnum;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactDockerSearchReqDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactDockerSearchResultDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactMavenSearchReqDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactMavenSearchResultDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactRepositoryDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ARTIFACT_REGISTRY_DELETE_FAIL_REPOSITORY_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ARTIFACT_REGISTRY_NAME_DUPLICATE;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ARTIFACT_REGISTRY_PASSWORD_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ARTIFACT_REPOSITORY_FORMAT_NOT_SUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ArtifactRegistryServiceImpl} 的单元测试。
 */
public class ArtifactRegistryServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ArtifactRegistryServiceImpl artifactRegistryService;

    @Mock
    private ArtifactRegistryMapper artifactRegistryMapper;
    @Mock
    private ArtifactRepositoryMapper artifactRepositoryMapper;
    @Mock
    private ArtifactRegistryClientFactory artifactRegistryClientFactory;
    @Mock
    private ArtifactRegistryClient artifactRegistryClient;

    @Test
    public void testCreateArtifactRegistry_success() {
        // 准备参数
        ArtifactRegistrySaveReqVO reqVO = buildSaveReqVO();
        doAnswer(invocation -> {
            ArtifactRegistryDO registry = invocation.getArgument(0);
            registry.setId(10L);
            return 1;
        }).when(artifactRegistryMapper).insert(any(ArtifactRegistryDO.class));

        // 调用
        Long id = artifactRegistryService.createArtifactRegistry(reqVO);

        // 断言
        assertEquals(10L, id);
        ArgumentCaptor<ArtifactRegistryDO> captor = ArgumentCaptor.forClass(ArtifactRegistryDO.class);
        verify(artifactRegistryMapper).insert(captor.capture());
        ArtifactRegistryDO registry = captor.getValue();
        assertEquals("公司 Nexus", registry.getName());
        assertEquals("https://nexus.example.com", registry.getServerUrl());
        assertEquals("secret", registry.getPassword());
        assertEquals("****cret", registry.getPasswordMask());
    }

    @Test
    public void testCreateArtifactRegistry_duplicateName() {
        // 准备参数
        ArtifactRegistrySaveReqVO reqVO = buildSaveReqVO();
        ArtifactRegistryDO oldRegistry = buildRegistry();
        oldRegistry.setId(99L);
        when(artifactRegistryMapper.selectByName(eq("公司 Nexus"))).thenReturn(oldRegistry);

        // 调用并断言
        assertServiceException(() -> artifactRegistryService.createArtifactRegistry(reqVO),
                ARTIFACT_REGISTRY_NAME_DUPLICATE);
        verify(artifactRegistryMapper, never()).insert(any(ArtifactRegistryDO.class));
    }

    @Test
    public void testCreateArtifactRegistry_missingPassword() {
        // 准备参数
        ArtifactRegistrySaveReqVO reqVO = buildSaveReqVO();
        reqVO.setPassword(null);

        // 调用并断言
        assertServiceException(() -> artifactRegistryService.createArtifactRegistry(reqVO),
                ARTIFACT_REGISTRY_PASSWORD_REQUIRED);
    }

    @Test
    public void testUpdateArtifactRegistry_keepOldPassword() {
        // 准备参数
        ArtifactRegistrySaveReqVO reqVO = buildSaveReqVO();
        reqVO.setId(10L);
        reqVO.setPassword(null);
        ArtifactRegistryDO oldRegistry = buildRegistry();
        oldRegistry.setPassword("old-secret");
        oldRegistry.setPasswordMask("****cret");
        when(artifactRegistryMapper.selectById(eq(10L))).thenReturn(oldRegistry);

        // 调用
        artifactRegistryService.updateArtifactRegistry(reqVO);

        // 断言
        ArgumentCaptor<ArtifactRegistryDO> captor = ArgumentCaptor.forClass(ArtifactRegistryDO.class);
        verify(artifactRegistryMapper).updateById(captor.capture());
        assertEquals("old-secret", captor.getValue().getPassword());
        assertEquals("****cret", captor.getValue().getPasswordMask());
    }

    @Test
    public void testDeleteArtifactRegistry_repositoryExists() {
        // 准备参数
        when(artifactRegistryMapper.selectById(eq(10L))).thenReturn(buildRegistry());
        when(artifactRepositoryMapper.selectCountByRegistryId(eq(10L))).thenReturn(1L);

        // 调用并断言
        assertServiceException(() -> artifactRegistryService.deleteArtifactRegistry(10L),
                ARTIFACT_REGISTRY_DELETE_FAIL_REPOSITORY_EXISTS);
    }

    @Test
    public void testSyncRepositories_mavenAndDockerOnly() {
        // 准备参数
        ArtifactRegistryDO registry = buildRegistry();
        when(artifactRegistryMapper.selectById(eq(10L))).thenReturn(registry);
        when(artifactRegistryClientFactory.getClient(eq(registry))).thenReturn(artifactRegistryClient);
        when(artifactRegistryClient.listRepositories(eq(registry))).thenReturn(List.of(
                buildRepositoryDTO("maven-releases", ArtifactRepositoryFormatEnum.MAVEN2.getFormat()),
                buildRepositoryDTO("gigi-docker", ArtifactRepositoryFormatEnum.DOCKER.getFormat()),
                buildRepositoryDTO("npm-public", ArtifactRepositoryFormatEnum.NPM.getFormat())));
        when(artifactRepositoryMapper.selectListByRegistryIdAndFormat(eq(10L), eq(null)))
                .thenReturn(List.of(buildRepositoryDO(), buildDockerRepositoryDO()));

        // 调用
        List<ArtifactRepositoryDO> repositories = artifactRegistryService.syncRepositories(10L);

        // 断言
        assertEquals(2, repositories.size());
        ArgumentCaptor<ArtifactRepositoryDO> captor = ArgumentCaptor.forClass(ArtifactRepositoryDO.class);
        verify(artifactRepositoryMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals("maven-releases", captor.getAllValues().get(0).getRepositoryName());
        assertEquals(ArtifactRepositoryFormatEnum.MAVEN2.getFormat(), captor.getAllValues().get(0).getFormat());
        assertEquals("gigi-docker", captor.getAllValues().get(1).getRepositoryName());
        assertEquals(ArtifactRepositoryFormatEnum.DOCKER.getFormat(), captor.getAllValues().get(1).getFormat());
    }

    @Test
    public void testGetRepositories_unsupportedFormatRejected() {
        // 准备参数
        when(artifactRegistryMapper.selectById(eq(10L))).thenReturn(buildRegistry());

        // 调用并断言
        assertServiceException(() -> artifactRegistryService.getRepositories(10L, ArtifactRepositoryFormatEnum.NPM.getFormat()),
                ARTIFACT_REPOSITORY_FORMAT_NOT_SUPPORTED);
    }

    @Test
    public void testSearchMaven_byRepositoryId() {
        // 准备参数
        ArtifactRegistryDO registry = buildRegistry();
        ArtifactRepositoryDO repository = buildRepositoryDO();
        ArtifactMavenSearchReqVO reqVO = new ArtifactMavenSearchReqVO();
        reqVO.setRegistryId(10L);
        reqVO.setRepositoryId(20L);
        reqVO.setGroupId("cn.iocoder.cloud");
        reqVO.setArtifactId("yudao-module-devops-api");
        reqVO.setVersion("1.0.0");
        reqVO.setContinuationToken("next");
        ArtifactMavenSearchResultDTO resultDTO = new ArtifactMavenSearchResultDTO();
        resultDTO.setContinuationToken("next-2");
        when(artifactRegistryMapper.selectById(eq(10L))).thenReturn(registry);
        when(artifactRepositoryMapper.selectById(eq(20L))).thenReturn(repository);
        when(artifactRegistryClientFactory.getClient(eq(registry))).thenReturn(artifactRegistryClient);
        when(artifactRegistryClient.searchMaven(eq(registry), any(ArtifactMavenSearchReqDTO.class))).thenReturn(resultDTO);

        // 调用
        ArtifactMavenSearchResultDTO result = artifactRegistryService.searchMaven(reqVO);

        // 断言
        assertEquals("next-2", result.getContinuationToken());
        ArgumentCaptor<ArtifactMavenSearchReqDTO> captor = ArgumentCaptor.forClass(ArtifactMavenSearchReqDTO.class);
        verify(artifactRegistryClient).searchMaven(eq(registry), captor.capture());
        assertEquals("maven-releases", captor.getValue().getRepositoryName());
        assertEquals("cn.iocoder.cloud", captor.getValue().getGroupId());
        assertEquals("yudao-module-devops-api", captor.getValue().getArtifactId());
        assertEquals("1.0.0", captor.getValue().getVersion());
        assertEquals("next", captor.getValue().getContinuationToken());
    }

    @Test
    public void testSearchDocker_byRepositoryId() {
        // 准备参数
        ArtifactRegistryDO registry = buildRegistry();
        ArtifactRepositoryDO repository = buildDockerRepositoryDO();
        ArtifactDockerSearchReqVO reqVO = new ArtifactDockerSearchReqVO();
        reqVO.setRegistryId(10L);
        reqVO.setRepositoryId(30L);
        reqVO.setKeyword("yudao");
        reqVO.setImageName("gigi-docker/yudao-gateway");
        reqVO.setTag("uat-1");
        reqVO.setContinuationToken("next");
        ArtifactDockerSearchResultDTO resultDTO = new ArtifactDockerSearchResultDTO();
        resultDTO.setContinuationToken("next-2");
        when(artifactRegistryMapper.selectById(eq(10L))).thenReturn(registry);
        when(artifactRepositoryMapper.selectById(eq(30L))).thenReturn(repository);
        when(artifactRegistryClientFactory.getClient(eq(registry))).thenReturn(artifactRegistryClient);
        when(artifactRegistryClient.searchDocker(eq(registry), any(ArtifactDockerSearchReqDTO.class))).thenReturn(resultDTO);

        // 调用
        ArtifactDockerSearchResultDTO result = artifactRegistryService.searchDocker(reqVO);

        // 断言
        assertEquals("next-2", result.getContinuationToken());
        ArgumentCaptor<ArtifactDockerSearchReqDTO> captor = ArgumentCaptor.forClass(ArtifactDockerSearchReqDTO.class);
        verify(artifactRegistryClient).searchDocker(eq(registry), captor.capture());
        assertEquals("gigi-docker", captor.getValue().getRepositoryName());
        assertEquals("yudao", captor.getValue().getKeyword());
        assertEquals("gigi-docker/yudao-gateway", captor.getValue().getImageName());
        assertEquals("uat-1", captor.getValue().getTag());
        assertEquals("next", captor.getValue().getContinuationToken());
    }

    private ArtifactRegistrySaveReqVO buildSaveReqVO() {
        ArtifactRegistrySaveReqVO reqVO = new ArtifactRegistrySaveReqVO();
        reqVO.setName("公司 Nexus");
        reqVO.setProviderType(ArtifactRegistryProviderTypeEnum.NEXUS3.getProviderType());
        reqVO.setServerUrl("https://nexus.example.com/");
        reqVO.setAuthType(ArtifactRegistryAuthTypeEnum.USERNAME_PASSWORD.getAuthType());
        reqVO.setUsername("admin");
        reqVO.setPassword("secret");
        reqVO.setStatus(0);
        return reqVO;
    }

    private ArtifactRegistryDO buildRegistry() {
        ArtifactRegistryDO registry = new ArtifactRegistryDO();
        registry.setId(10L);
        registry.setName("公司 Nexus");
        registry.setProviderType(ArtifactRegistryProviderTypeEnum.NEXUS3.getProviderType());
        registry.setServerUrl("https://nexus.example.com");
        registry.setAuthType(ArtifactRegistryAuthTypeEnum.USERNAME_PASSWORD.getAuthType());
        registry.setUsername("admin");
        registry.setPassword("secret");
        registry.setStatus(0);
        return registry;
    }

    private ArtifactRepositoryDTO buildRepositoryDTO(String name, String format) {
        ArtifactRepositoryDTO dto = new ArtifactRepositoryDTO();
        dto.setRepositoryName(name);
        dto.setFormat(format);
        dto.setRepositoryType(ArtifactRepositoryTypeEnum.HOSTED.getRepositoryType());
        dto.setUrl("https://nexus.example.com/repository/" + name + "/");
        dto.setOnline(true);
        return dto;
    }

    private ArtifactRepositoryDO buildRepositoryDO() {
        ArtifactRepositoryDO repository = new ArtifactRepositoryDO();
        repository.setId(20L);
        repository.setRegistryId(10L);
        repository.setRepositoryName("maven-releases");
        repository.setFormat(ArtifactRepositoryFormatEnum.MAVEN2.getFormat());
        repository.setRepositoryType(ArtifactRepositoryTypeEnum.HOSTED.getRepositoryType());
        repository.setUrl("https://nexus.example.com/repository/maven-releases/");
        repository.setOnline(true);
        repository.setStatus(0);
        return repository;
    }

    private ArtifactRepositoryDO buildDockerRepositoryDO() {
        ArtifactRepositoryDO repository = new ArtifactRepositoryDO();
        repository.setId(30L);
        repository.setRegistryId(10L);
        repository.setRepositoryName("gigi-docker");
        repository.setFormat(ArtifactRepositoryFormatEnum.DOCKER.getFormat());
        repository.setRepositoryType(ArtifactRepositoryTypeEnum.HOSTED.getRepositoryType());
        repository.setUrl("https://nexus.example.com/repository/gigi-docker/");
        repository.setOnline(true);
        repository.setStatus(0);
        return repository;
    }

}
