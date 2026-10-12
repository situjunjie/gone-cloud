package cn.iocoder.yudao.module.devops.service.applicationorchestration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationComponentVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationConfigVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationSaveDraftReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationValidationRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.applicationorchestration.ApplicationOrchestrationDO;
import cn.iocoder.yudao.module.devops.dal.mysql.applicationorchestration.ApplicationOrchestrationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_ORCHESTRATION_INVALID;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_ORCHESTRATION_TYPE_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_ORCHESTRATION_UPDATE_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationOrchestrationServiceImpl} 的单元测试。
 */
public class ApplicationOrchestrationServiceImplTest extends BaseMockitoUnitTest {

    private static final String TYPE = "K8S_DEPLOYMENT";

    @InjectMocks
    private ApplicationOrchestrationServiceImpl orchestrationService;

    @Mock
    private ApplicationOrchestrationMapper applicationOrchestrationMapper;
    @Mock
    private ApplicationOrchestrationComponentTemplateService componentTemplateService;
    @Mock
    private ApplicationOrchestrationValidationService validationService;
    @Mock
    private ApplicationMapper applicationMapper;

    @Test
    public void testGetOrchestration_notExists_returnsAppSummary() {
        ApplicationDO application = buildApplication(1024L);
        when(applicationMapper.selectById(eq(1024L))).thenReturn(application);
        when(applicationOrchestrationMapper.selectByAppIdAndType(eq(1024L), eq(TYPE))).thenReturn(null);

        ApplicationOrchestrationRespVO respVO = orchestrationService.getOrchestration(1024L, TYPE);

        assertNull(respVO.getId());
        assertEquals(1024L, respVO.getAppId());
        assertEquals("gone-api", respVO.getAppKey());
        assertEquals("Gone API", respVO.getAppName());
        assertNotNull(respVO.getConfig());
        assertTrue(respVO.getConfig().getComponents().isEmpty());
    }

    @Test
    public void testGetOrchestration_typeNotSupported() {
        assertServiceException(() -> orchestrationService.getOrchestration(1024L, "HOST_DEPLOYMENT"),
                APPLICATION_ORCHESTRATION_TYPE_NOT_SUPPORTED);
    }

    @Test
    public void testApplicationMissing_preservesErrorAndDoesNotReadOrWriteDraft() {
        assertServiceException(() -> orchestrationService.getOrchestration(1024L, TYPE),
                cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_NOT_EXISTS);
        assertServiceException(() -> orchestrationService.validate(buildSaveReqVO(1024L, null)),
                cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_NOT_EXISTS);
        assertServiceException(() -> orchestrationService.saveDraft(buildSaveReqVO(1024L, null)),
                cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_NOT_EXISTS);
        org.mockito.Mockito.verifyNoInteractions(applicationOrchestrationMapper, validationService);
    }

    @Test
    public void testSaveDraft_firstCreate() {
        ApplicationOrchestrationSaveDraftReqVO reqVO = buildSaveReqVO(1024L, null);
        when(applicationMapper.selectById(eq(1024L))).thenReturn(buildApplication(1024L));
        when(validationService.validate(any())).thenReturn(validResult(reqVO.getConfig()));
        when(applicationOrchestrationMapper.selectByAppIdAndType(eq(1024L), eq(TYPE))).thenReturn(null);
        doAnswer(invocation -> {
            ApplicationOrchestrationDO orchestration = invocation.getArgument(0);
            orchestration.setId(50L);
            return 1;
        }).when(applicationOrchestrationMapper).insert(any(ApplicationOrchestrationDO.class));

        Long id = orchestrationService.saveDraft(reqVO);

        assertEquals(50L, id);
        ArgumentCaptor<ApplicationOrchestrationDO> captor = ArgumentCaptor.forClass(ApplicationOrchestrationDO.class);
        verify(applicationOrchestrationMapper).insert(captor.capture());
        ApplicationOrchestrationDO inserted = captor.getValue();
        assertEquals(1024L, inserted.getAppId());
        assertEquals(TYPE, inserted.getOrchestrationType());
        assertEquals("1.0", inserted.getSchemaVersion());
        assertNotNull(inserted.getComponentJson());
    }

    @Test
    public void testSaveDraft_overwriteExisting() {
        LocalDateTime updateTime = LocalDateTime.now();
        ApplicationOrchestrationSaveDraftReqVO reqVO = buildSaveReqVO(1024L, updateTime);
        ApplicationOrchestrationDO existing = new ApplicationOrchestrationDO();
        existing.setId(60L);
        existing.setUpdateTime(updateTime);
        when(applicationMapper.selectById(eq(1024L))).thenReturn(buildApplication(1024L));
        when(validationService.validate(any())).thenReturn(validResult(reqVO.getConfig()));
        when(applicationOrchestrationMapper.selectByAppIdAndType(eq(1024L), eq(TYPE))).thenReturn(existing);
        when(applicationOrchestrationMapper.updateByIdAndUpdateTime(any(), eq(updateTime))).thenReturn(1);

        Long id = orchestrationService.saveDraft(reqVO);

        assertEquals(60L, id);
        verify(applicationOrchestrationMapper, never()).insert(any(ApplicationOrchestrationDO.class));
        verify(applicationOrchestrationMapper).updateByIdAndUpdateTime(any(), eq(updateTime));
    }

    @Test
    public void testSaveDraft_concurrentConflict() {
        LocalDateTime updateTime = LocalDateTime.now();
        ApplicationOrchestrationSaveDraftReqVO reqVO = buildSaveReqVO(1024L, updateTime);
        ApplicationOrchestrationDO existing = new ApplicationOrchestrationDO();
        existing.setId(60L);
        existing.setUpdateTime(updateTime);
        when(applicationMapper.selectById(eq(1024L))).thenReturn(buildApplication(1024L));
        when(validationService.validate(any())).thenReturn(validResult(reqVO.getConfig()));
        when(applicationOrchestrationMapper.selectByAppIdAndType(eq(1024L), eq(TYPE))).thenReturn(existing);
        when(applicationOrchestrationMapper.updateByIdAndUpdateTime(any(), eq(updateTime))).thenReturn(0);

        assertServiceException(() -> orchestrationService.saveDraft(reqVO), APPLICATION_ORCHESTRATION_UPDATE_CONFLICT);
    }

    @Test
    public void testSaveDraft_validationFail_doesNotWrite() {
        ApplicationOrchestrationSaveDraftReqVO reqVO = buildSaveReqVO(1024L, null);
        when(applicationMapper.selectById(eq(1024L))).thenReturn(buildApplication(1024L));
        when(validationService.validate(any())).thenReturn(invalidResult(reqVO.getConfig()));

        assertServiceException(() -> orchestrationService.saveDraft(reqVO), APPLICATION_ORCHESTRATION_INVALID,
                "demo-config: YAML 解析失败");
        verify(applicationOrchestrationMapper, never()).insert(any(ApplicationOrchestrationDO.class));
        verify(applicationOrchestrationMapper, never()).updateByIdAndUpdateTime(any(), any());
    }

    private ApplicationDO buildApplication(Long id) {
        ApplicationDO application = new ApplicationDO();
        application.setId(id);
        application.setAppKey("gone-api");
        application.setName("Gone API");
        return application;
    }

    private ApplicationOrchestrationSaveDraftReqVO buildSaveReqVO(Long appId, LocalDateTime updateTime) {
        ApplicationOrchestrationSaveDraftReqVO reqVO = new ApplicationOrchestrationSaveDraftReqVO();
        reqVO.setAppId(appId);
        reqVO.setOrchestrationType(TYPE);
        reqVO.setConfig(buildConfig());
        reqVO.setUpdateTime(updateTime);
        return reqVO;
    }

    private ApplicationOrchestrationConfigVO buildConfig() {
        ApplicationOrchestrationComponentVO component = new ApplicationOrchestrationComponentVO();
        component.setId("demo-config");
        component.setName("demo-config");
        component.setTemplateKey("CONFIG_MAP");
        component.setKind("ConfigMap");
        component.setApplyOrder(1);
        component.setDependsOn(new ArrayList<>());
        component.setEnabled(Boolean.TRUE);
        component.setYaml("apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: demo-config\ndata: {}\n");
        ApplicationOrchestrationConfigVO config = new ApplicationOrchestrationConfigVO();
        config.setSchemaVersion("1.0");
        config.setComponents(new ArrayList<>(List.of(component)));
        return config;
    }

    private ApplicationOrchestrationValidationService.ValidationResult validResult(ApplicationOrchestrationConfigVO config) {
        ApplicationOrchestrationConfigVO normalized = JsonUtils.parseObject(JsonUtils.toJsonString(config),
                ApplicationOrchestrationConfigVO.class);
        return new ApplicationOrchestrationValidationService.ValidationResult(normalized, new ArrayList<>(), new ArrayList<>());
    }

    private ApplicationOrchestrationValidationService.ValidationResult invalidResult(ApplicationOrchestrationConfigVO config) {
        return new ApplicationOrchestrationValidationService.ValidationResult(config, new ArrayList<>(),
                List.of(new cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationValidationMessageRespVO(
                        "demo-config", "yaml", "YAML_INVALID", "YAML 解析失败")));
    }

}
