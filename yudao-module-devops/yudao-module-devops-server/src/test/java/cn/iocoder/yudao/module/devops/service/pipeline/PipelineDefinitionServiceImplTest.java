package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelinePublishReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineSaveDraftReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionVersionMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineDefinitionVersionStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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

    private PipelineNodeRegistryServiceImpl nodeRegistryService;
    private PipelineSpecValidationServiceImpl validationService;

    @BeforeEach
    public void setUpPipelineServices() {
        nodeRegistryService = new PipelineNodeRegistryServiceImpl();
        validationService = new PipelineSpecValidationServiceImpl();
        ReflectionTestUtils.setField(validationService, "pipelineNodeRegistryService", nodeRegistryService);
        ReflectionTestUtils.setField(pipelineDefinitionService, "pipelineSpecValidationService", validationService);
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
        when(pipelineDefinitionVersionMapper.selectById(eq(200L))).thenReturn(draft);
        when(pipelineDefinitionVersionMapper.selectListByDefinitionId(eq(100L))).thenReturn(List.of(draft));
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

        ArgumentCaptor<ApplicationEnvDO> applicationEnvCaptor = ArgumentCaptor.forClass(ApplicationEnvDO.class);
        verify(applicationEnvMapper).updateById(applicationEnvCaptor.capture());
        assertEquals(10L, applicationEnvCaptor.getValue().getId());
        assertEquals(100L, applicationEnvCaptor.getValue().getPipelineDefinitionId());
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

    private String validSpecJson() {
        return """
                {
                  "dslVersion": "1.0",
                  "sources": {
                    "repo": {"type": "gitlab", "endpoint": "https://gitlab.example.com/gone/api.git", "branch": "main"}
                  },
                  "stages": {
                    "release": {
                      "name": "发布",
                      "jobs": {
                        "merge": {
                          "name": "代码合并",
                          "steps": {
                            "code_merge": {
                              "name": "代码合并",
                              "step": "CodeMerge",
                              "with": {"baseBranch": "main", "targetBranch": "release/test"}
                            }
                          }
                        },
                        "build": {
                          "name": "构建",
                          "needs": ["merge"],
                          "runsOn": {"group": "local-docker/default", "container": "maven:3.9-eclipse-temurin-17"},
                          "steps": {
                            "package": {
                              "name": "构建",
                              "step": "Command",
                              "with": {"run": "mvn -DskipTests package", "shellType": "bash"}
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;
    }

}
