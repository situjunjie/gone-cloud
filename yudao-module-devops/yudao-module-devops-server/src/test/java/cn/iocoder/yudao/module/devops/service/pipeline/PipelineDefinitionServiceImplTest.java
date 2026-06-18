package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelinePublishReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineRollbackReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineCacheClearReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineSaveDraftReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionVersionMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineDefinitionVersionStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCacheConfig;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCacheConfigResolver;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineWorkspaceService;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_CACHE_CLEAR_PATH_INVALID;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_CACHE_CLEAR_RUNNING;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_ROLLBACK_TARGET_INVALID;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_VERSION_NOT_IN_DEFINITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PipelineDefinitionServiceImpl} 的单元测试。
 */
public class PipelineDefinitionServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private PipelineDefinitionServiceImpl pipelineDefinitionService;

    @Mock
    private PipelineDefinitionMapper pipelineDefinitionMapper;
    @Mock
    private PipelineDefinitionVersionMapper pipelineDefinitionVersionMapper;
    @Mock
    private ApplicationEnvMapper applicationEnvMapper;
    @Mock
    private PipelineRunMapper pipelineRunMapper;
    @Mock
    private PipelineWorkspaceService pipelineWorkspaceService;

    private PipelineNodeRegistryServiceImpl nodeRegistryService;
    private PipelineSpecValidationServiceImpl validationService;
    private PipelineCacheConfigResolver cacheConfigResolver;

    @BeforeEach
    public void setUpPipelineServices() {
        nodeRegistryService = new PipelineNodeRegistryServiceImpl();
        validationService = new PipelineSpecValidationServiceImpl();
        cacheConfigResolver = new PipelineCacheConfigResolver();
        ReflectionTestUtils.setField(validationService, "pipelineNodeRegistryService", nodeRegistryService);
        ReflectionTestUtils.setField(pipelineDefinitionService, "pipelineSpecValidationService", validationService);
        ReflectionTestUtils.setField(pipelineDefinitionService, "pipelineCacheConfigResolver", cacheConfigResolver);
    }

    @Test
    public void testPublish_evictCurrentRunCache() throws Exception {
        // 调用
        Method method = PipelineDefinitionServiceImpl.class.getMethod("publish", PipelinePublishReqVO.class, Long.class);
        CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);

        // 断言
        assertEquals(RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN, cacheEvict.value()[0]);
        assertEquals("#root.target.getApplicationEnvIdByDefinitionId(#reqVO.definitionId)", cacheEvict.key());
    }

    @Test
    public void testSaveDraft_createDefinitionAndDraft() {
        // 准备参数
        PipelineSaveDraftReqVO reqVO = buildSaveDraftReqVO();
        when(applicationEnvMapper.selectById(eq(10L))).thenReturn(buildApplicationEnv());
        when(pipelineDefinitionMapper.selectByApplicationEnvId(eq(10L))).thenReturn(null);
        doAnswer(invocation -> {
            PipelineDefinitionDO definition = invocation.getArgument(0);
            definition.setId(100L);
            return 1;
        }).when(pipelineDefinitionMapper).insert(any(PipelineDefinitionDO.class));
        doAnswer(invocation -> {
            PipelineDefinitionVersionDO version = invocation.getArgument(0);
            version.setId(200L);
            return 1;
        }).when(pipelineDefinitionVersionMapper).insert(any(PipelineDefinitionVersionDO.class));

        // 调用
        Long draftVersionId = pipelineDefinitionService.saveDraft(reqVO);

        // 断言
        assertEquals(200L, draftVersionId);
        ArgumentCaptor<PipelineDefinitionDO> definitionCaptor = ArgumentCaptor.forClass(PipelineDefinitionDO.class);
        verify(pipelineDefinitionMapper).insert(definitionCaptor.capture());
        assertEquals("app-env-10", definitionCaptor.getValue().getDefinitionKey());
        assertEquals(1L, definitionCaptor.getValue().getAppId());

        ArgumentCaptor<PipelineDefinitionVersionDO> versionCaptor = ArgumentCaptor.forClass(PipelineDefinitionVersionDO.class);
        verify(pipelineDefinitionVersionMapper).insert(versionCaptor.capture());
        PipelineDefinitionVersionDO version = versionCaptor.getValue();
        assertEquals(0, version.getVersionNo());
        assertEquals(PipelineDefinitionVersionStatusEnum.DRAFT.getStatus(), version.getVersionStatus());
        PipelineCacheConfig cacheConfig = JsonUtils.parseObject(version.getCacheConfigJson(), PipelineCacheConfig.class);
        assertEquals("/root/.m2", cacheConfig.getDirectories().get(0).getPath());
    }

    @Test
    public void testPublish_success() {
        // 准备参数
        PipelinePublishReqVO reqVO = new PipelinePublishReqVO();
        reqVO.setDefinitionId(100L);
        reqVO.setDraftVersionId(200L);
        reqVO.setVersionName("v1");

        PipelineDefinitionDO definition = new PipelineDefinitionDO();
        definition.setId(100L);
        definition.setApplicationEnvId(10L);
        when(pipelineDefinitionMapper.selectById(eq(100L))).thenReturn(definition);

        PipelineDefinitionVersionDO draft = new PipelineDefinitionVersionDO();
        draft.setId(200L);
        draft.setDefinitionId(100L);
        draft.setVersionNo(0);
        draft.setVersionStatus(PipelineDefinitionVersionStatusEnum.DRAFT.getStatus());
        draft.setDiagramJson("{}");
        draft.setSpecJson(validSpecJson());
        draft.setCacheConfigJson(JsonUtils.toJsonString(cacheConfig("/root/.m2")));
        draft.setValidationResultJson("{\"valid\":true}");
        when(pipelineDefinitionVersionMapper.selectById(eq(200L))).thenReturn(draft);
        when(pipelineDefinitionVersionMapper.selectPublishedListByDefinitionId(eq(100L))).thenReturn(List.of());
        doAnswer(invocation -> {
            PipelineDefinitionVersionDO version = invocation.getArgument(0);
            version.setId(300L);
            return 1;
        }).when(pipelineDefinitionVersionMapper).insert(any(PipelineDefinitionVersionDO.class));

        // 调用
        Long publishedVersionId = pipelineDefinitionService.publish(reqVO, 99L);

        // 断言
        assertEquals(300L, publishedVersionId);
        ArgumentCaptor<PipelineDefinitionVersionDO> versionCaptor = ArgumentCaptor.forClass(PipelineDefinitionVersionDO.class);
        verify(pipelineDefinitionVersionMapper).insert(versionCaptor.capture());
        PipelineDefinitionVersionDO published = versionCaptor.getValue();
        assertEquals(1, published.getVersionNo());
        assertEquals(PipelineDefinitionVersionStatusEnum.PUBLISHED.getStatus(), published.getVersionStatus());
        assertEquals(99L, published.getPublishedBy());
        assertEquals(Boolean.TRUE,
                JsonUtils.parseObject(published.getValidationResultJson(), PipelineValidationRespVO.class).getValid());
        PipelineCacheConfig cacheConfig = JsonUtils.parseObject(published.getCacheConfigJson(), PipelineCacheConfig.class);
        assertEquals(1, cacheConfig.getDirectories().size());
        assertEquals("/root/.m2", cacheConfig.getDirectories().get(0).getPath());

        ArgumentCaptor<ApplicationEnvDO> applicationEnvCaptor = ArgumentCaptor.forClass(ApplicationEnvDO.class);
        verify(applicationEnvMapper).updateById(applicationEnvCaptor.capture());
        assertEquals(10L, applicationEnvCaptor.getValue().getId());
        assertEquals(100L, applicationEnvCaptor.getValue().getPipelineDefinitionId());
    }

    @Test
    public void testRollback_success() {
        PipelineRollbackReqVO reqVO = new PipelineRollbackReqVO();
        reqVO.setDefinitionId(100L);
        reqVO.setTargetVersionId(600L);
        reqVO.setRollbackReason("发布后异常，回退到稳定版本");

        PipelineDefinitionDO definition = new PipelineDefinitionDO();
        definition.setId(100L);
        definition.setApplicationEnvId(10L);
        definition.setPublishedVersionId(1000L);
        when(pipelineDefinitionMapper.selectById(eq(100L))).thenReturn(definition);

        PipelineDefinitionVersionDO currentVersion = new PipelineDefinitionVersionDO();
        currentVersion.setId(1000L);
        currentVersion.setDefinitionId(100L);
        currentVersion.setVersionNo(10);
        currentVersion.setVersionStatus(PipelineDefinitionVersionStatusEnum.PUBLISHED.getStatus());

        PipelineDefinitionVersionDO targetVersion = new PipelineDefinitionVersionDO();
        targetVersion.setId(600L);
        targetVersion.setDefinitionId(100L);
        targetVersion.setVersionNo(6);
        targetVersion.setVersionStatus(PipelineDefinitionVersionStatusEnum.PUBLISHED.getStatus());
        targetVersion.setDiagramJson("{\"nodes\":[]}");
        targetVersion.setSpecJson(validSpecJson());
        targetVersion.setNodeSchemaVersion("1.0");
        targetVersion.setCacheConfigJson(JsonUtils.toJsonString(cacheConfig("/root/.npm")));
        targetVersion.setValidationResultJson("{\"valid\":true}");

        when(pipelineDefinitionVersionMapper.selectById(eq(1000L))).thenReturn(currentVersion);
        when(pipelineDefinitionVersionMapper.selectById(eq(600L))).thenReturn(targetVersion);
        when(pipelineDefinitionVersionMapper.selectPublishedListByDefinitionId(eq(100L)))
                .thenReturn(List.of(targetVersion, currentVersion));
        doAnswer(invocation -> {
            PipelineDefinitionVersionDO version = invocation.getArgument(0);
            version.setId(1100L);
            return 1;
        }).when(pipelineDefinitionVersionMapper).insert(any(PipelineDefinitionVersionDO.class));

        Long rollbackVersionId = pipelineDefinitionService.rollback(reqVO, 99L);

        assertEquals(1100L, rollbackVersionId);
        ArgumentCaptor<PipelineDefinitionVersionDO> versionCaptor = ArgumentCaptor.forClass(PipelineDefinitionVersionDO.class);
        verify(pipelineDefinitionVersionMapper).insert(versionCaptor.capture());
        PipelineDefinitionVersionDO rollbackVersion = versionCaptor.getValue();
        assertEquals(11, rollbackVersion.getVersionNo());
        assertEquals(PipelineDefinitionVersionStatusEnum.PUBLISHED.getStatus(), rollbackVersion.getVersionStatus());
        assertEquals(600L, rollbackVersion.getRollbackFromVersionId());
        assertEquals(6, rollbackVersion.getRollbackFromVersionNo());
        assertEquals(1000L, rollbackVersion.getBasedOnCurrentVersionId());
        assertEquals(10, rollbackVersion.getBasedOnCurrentVersionNo());
        assertEquals("发布后异常，回退到稳定版本", rollbackVersion.getRollbackReason());
        assertEquals("{\"nodes\":[]}", rollbackVersion.getDiagramJson());
        assertEquals(validSpecJson(), rollbackVersion.getSpecJson());
        assertEquals(99L, rollbackVersion.getPublishedBy());
        PipelineCacheConfig cacheConfig = JsonUtils.parseObject(rollbackVersion.getCacheConfigJson(), PipelineCacheConfig.class);
        assertEquals(1, cacheConfig.getDirectories().size());
        assertEquals("/root/.npm", cacheConfig.getDirectories().get(0).getPath());

        ArgumentCaptor<PipelineDefinitionDO> definitionCaptor = ArgumentCaptor.forClass(PipelineDefinitionDO.class);
        verify(pipelineDefinitionMapper).updateById(definitionCaptor.capture());
        assertEquals(1100L, definitionCaptor.getValue().getPublishedVersionId());
    }

    @Test
    public void testClearCache_success() {
        PipelineCacheClearReqVO reqVO = new PipelineCacheClearReqVO();
        reqVO.setDefinitionId(100L);
        reqVO.setPaths(List.of("/root/.m2"));
        PipelineDefinitionDO definition = buildDefinition();
        definition.setPublishedVersionId(300L);
        when(pipelineDefinitionMapper.selectById(eq(100L))).thenReturn(definition);
        PipelineDefinitionVersionDO published = buildVersion(300L, "/root/.m2");
        when(pipelineDefinitionVersionMapper.selectById(eq(300L))).thenReturn(published);
        when(pipelineRunMapper.selectListByApplicationEnvIdAndStatuses(eq(10L), any())).thenReturn(List.of());
        when(pipelineWorkspaceService.listRecordedCachePaths(eq(definition))).thenReturn(Set.of());

        Boolean result = pipelineDefinitionService.clearCache(reqVO, 99L);

        assertEquals(Boolean.TRUE, result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> pathsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(pipelineWorkspaceService).clearDefinitionCache(eq(definition), pathsCaptor.capture());
        assertEquals(Set.of("/root/.m2"), Set.copyOf(pathsCaptor.getValue()));
    }

    @Test
    public void testClearCache_runningRejected() {
        PipelineCacheClearReqVO reqVO = new PipelineCacheClearReqVO();
        reqVO.setDefinitionId(100L);
        PipelineDefinitionDO definition = buildDefinition();
        when(pipelineDefinitionMapper.selectById(eq(100L))).thenReturn(definition);
        PipelineRunDO activeRun = new PipelineRunDO();
        activeRun.setDefinitionId(100L);
        activeRun.setRunStatus(PipelineRunStatusEnum.RUNNING.getStatus());
        when(pipelineRunMapper.selectListByApplicationEnvIdAndStatuses(eq(10L), any())).thenReturn(List.of(activeRun));

        assertServiceException(() -> pipelineDefinitionService.clearCache(reqVO, 99L), PIPELINE_CACHE_CLEAR_RUNNING);
    }

    @Test
    public void testClearCache_invalidPathRejected() {
        PipelineCacheClearReqVO reqVO = new PipelineCacheClearReqVO();
        reqVO.setDefinitionId(100L);
        reqVO.setPaths(List.of("/root/.npm"));
        PipelineDefinitionDO definition = buildDefinition();
        definition.setPublishedVersionId(300L);
        when(pipelineDefinitionMapper.selectById(eq(100L))).thenReturn(definition);
        when(pipelineDefinitionVersionMapper.selectById(eq(300L))).thenReturn(buildVersion(300L, "/root/.m2"));
        when(pipelineRunMapper.selectListByApplicationEnvIdAndStatuses(eq(10L), any())).thenReturn(List.of());
        when(pipelineWorkspaceService.listRecordedCachePaths(eq(definition))).thenReturn(Set.of());

        assertServiceException(() -> pipelineDefinitionService.clearCache(reqVO, 99L),
                PIPELINE_CACHE_CLEAR_PATH_INVALID, "/root/.npm");
    }

    @Test
    public void testClearCache_disabledVersionConfigDoesNotFallbackDefault() {
        PipelineCacheClearReqVO reqVO = new PipelineCacheClearReqVO();
        reqVO.setDefinitionId(100L);
        PipelineDefinitionDO definition = buildDefinition();
        definition.setPublishedVersionId(300L);
        PipelineDefinitionVersionDO version = buildVersion(300L, "/root/.m2");
        version.setCacheConfigJson(JsonUtils.toJsonString(cacheConfig("/root/.m2", false)));
        when(pipelineDefinitionMapper.selectById(eq(100L))).thenReturn(definition);
        when(pipelineDefinitionVersionMapper.selectById(eq(300L))).thenReturn(version);
        when(pipelineRunMapper.selectListByApplicationEnvIdAndStatuses(eq(10L), any())).thenReturn(List.of());
        when(pipelineWorkspaceService.listRecordedCachePaths(eq(definition))).thenReturn(Set.of());

        Boolean result = pipelineDefinitionService.clearCache(reqVO, 99L);

        assertEquals(Boolean.TRUE, result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> pathsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(pipelineWorkspaceService).clearDefinitionCache(eq(definition), pathsCaptor.capture());
        assertEquals(Set.of(), Set.copyOf(pathsCaptor.getValue()));
    }

    @Test
    public void testRollback_targetVersionNotInDefinition() {
        PipelineRollbackReqVO reqVO = new PipelineRollbackReqVO();
        reqVO.setDefinitionId(100L);
        reqVO.setTargetVersionId(600L);
        reqVO.setRollbackReason("回退");

        PipelineDefinitionDO definition = new PipelineDefinitionDO();
        definition.setId(100L);
        when(pipelineDefinitionMapper.selectById(eq(100L))).thenReturn(definition);

        PipelineDefinitionVersionDO targetVersion = new PipelineDefinitionVersionDO();
        targetVersion.setId(600L);
        targetVersion.setDefinitionId(999L);
        when(pipelineDefinitionVersionMapper.selectById(eq(600L))).thenReturn(targetVersion);

        assertServiceException(() -> pipelineDefinitionService.rollback(reqVO, 99L), PIPELINE_VERSION_NOT_IN_DEFINITION);
    }

    @Test
    public void testRollback_targetVersionIsCurrentPublishedVersion() {
        PipelineRollbackReqVO reqVO = new PipelineRollbackReqVO();
        reqVO.setDefinitionId(100L);
        reqVO.setTargetVersionId(1000L);
        reqVO.setRollbackReason("回退");

        PipelineDefinitionDO definition = new PipelineDefinitionDO();
        definition.setId(100L);
        definition.setPublishedVersionId(1000L);
        when(pipelineDefinitionMapper.selectById(eq(100L))).thenReturn(definition);

        PipelineDefinitionVersionDO currentVersion = new PipelineDefinitionVersionDO();
        currentVersion.setId(1000L);
        currentVersion.setDefinitionId(100L);
        currentVersion.setVersionNo(10);
        currentVersion.setVersionStatus(PipelineDefinitionVersionStatusEnum.PUBLISHED.getStatus());
        when(pipelineDefinitionVersionMapper.selectById(eq(1000L))).thenReturn(currentVersion);

        assertServiceException(() -> pipelineDefinitionService.rollback(reqVO, 99L), PIPELINE_ROLLBACK_TARGET_INVALID);
    }

    private PipelineSaveDraftReqVO buildSaveDraftReqVO() {
        PipelineSaveDraftReqVO reqVO = new PipelineSaveDraftReqVO();
        reqVO.setApplicationEnvId(10L);
        reqVO.setName("测试环境流水线");
        reqVO.setDiagramJson("{}");
        reqVO.setSpecJson(validSpecJson());
        return reqVO;
    }

    private ApplicationEnvDO buildApplicationEnv() {
        ApplicationEnvDO applicationEnv = new ApplicationEnvDO();
        applicationEnv.setId(10L);
        applicationEnv.setAppId(1L);
        return applicationEnv;
    }

    private PipelineDefinitionDO buildDefinition() {
        PipelineDefinitionDO definition = new PipelineDefinitionDO();
        definition.setId(100L);
        definition.setAppId(1L);
        definition.setApplicationEnvId(10L);
        return definition;
    }

    private PipelineDefinitionVersionDO buildVersion(Long id, String cachePath) {
        PipelineDefinitionVersionDO version = new PipelineDefinitionVersionDO();
        version.setId(id);
        version.setDefinitionId(100L);
        version.setVersionStatus(PipelineDefinitionVersionStatusEnum.PUBLISHED.getStatus());
        version.setCacheConfigJson(JsonUtils.toJsonString(cacheConfig(cachePath)));
        return version;
    }

    private PipelineCacheConfig cacheConfig(String path) {
        return cacheConfig(path, true);
    }

    private PipelineCacheConfig cacheConfig(String path, Boolean enabled) {
        PipelineCacheConfig config = new PipelineCacheConfig();
        PipelineCacheConfig.Directory directory = new PipelineCacheConfig.Directory();
        directory.setId("cache");
        directory.setPath(path);
        directory.setEnabled(enabled);
        config.setDirectories(List.of(directory));
        return config;
    }

    private String validSpecJson() {
        return """
                sources:
                  repo:
                    type: gitlab
                    endpoint: https://gitlab.example.com/gone/api.git
                    branch: main
                stages:
                  release:
                    name: 发布
                    jobs:
                      build:
                        name: 构建
                        runsOn:
                          group: local-docker/default
                          container: maven:3.9-eclipse-temurin-17
                        steps:
                          package:
                            name: 构建
                            step: Command
                            with:
                              run: mvn -DskipTests package
                """;
    }

}
