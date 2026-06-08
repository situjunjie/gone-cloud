package cn.iocoder.yudao.module.devops.service.pipeline.jenkins;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineJenkinsCallbackReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineJenkinsCallbackRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionVersionMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.jenkins.JenkinsProperties;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineSpecValidationServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.runtime.JenkinsPipelineNodeRuntimeHandler;
import cn.iocoder.yudao.module.devops.service.pipeline.runtime.MockPipelineNodeRuntimeHandler;
import cn.iocoder.yudao.module.devops.service.pipeline.runtime.PipelineNodeCallbackAction;
import cn.iocoder.yudao.module.devops.service.pipeline.runtime.PipelineNodeRuntimeSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PipelineJenkinsCallbackServiceImplTest extends BaseMockitoUnitTest {

    private PipelineJenkinsCallbackServiceImpl callbackService;

    @Mock
    private PipelineRunMapper pipelineRunMapper;
    @Mock
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Mock
    private PipelineDefinitionVersionMapper pipelineDefinitionVersionMapper;

    private JenkinsProperties jenkinsProperties;
    private MockPipelineNodeRuntimeHandler mockHandler;
    private JenkinsPipelineNodeRuntimeHandler jenkinsHandler;

    @BeforeEach
    public void setUp() {
        jenkinsProperties = new JenkinsProperties();
        jenkinsProperties.setCallbackToken("secret");

        PipelineSpecValidationServiceImpl validationService = new PipelineSpecValidationServiceImpl();
        ReflectionTestUtils.setField(validationService, "pipelineNodeRegistryService", new PipelineNodeRegistryServiceImpl());

        PipelineNodeRuntimeSupport runtimeSupport = new PipelineNodeRuntimeSupport();
        ReflectionTestUtils.setField(runtimeSupport, "pipelineRunMapper", pipelineRunMapper);
        ReflectionTestUtils.setField(runtimeSupport, "pipelineRunLogMapper", pipelineRunLogMapper);
        mockHandler = new MockPipelineNodeRuntimeHandler();
        ReflectionTestUtils.setField(mockHandler, "runtimeSupport", runtimeSupport);
        jenkinsHandler = new JenkinsPipelineNodeRuntimeHandler();
        ReflectionTestUtils.setField(jenkinsHandler, "runtimeSupport", runtimeSupport);
        callbackService = new PipelineJenkinsCallbackServiceImpl(List.of(mockHandler, jenkinsHandler));
        ReflectionTestUtils.setField(callbackService, "jenkinsProperties", jenkinsProperties);
        ReflectionTestUtils.setField(callbackService, "pipelineRunMapper", pipelineRunMapper);
        ReflectionTestUtils.setField(callbackService, "pipelineRunLogMapper", pipelineRunLogMapper);
        ReflectionTestUtils.setField(callbackService, "pipelineDefinitionVersionMapper", pipelineDefinitionVersionMapper);
        ReflectionTestUtils.setField(callbackService, "pipelineSpecValidationService", validationService);
    }

    @Test
    public void testHandleCallback_started() {
        // 准备参数
        mockBaseContext();
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("mock"))).thenReturn(null);
        doAnswer(invocation -> {
            PipelineRunLogDO log = invocation.getArgument(0);
            log.setId(1000L);
            return 1;
        }).when(pipelineRunLogMapper).insert(any(PipelineRunLogDO.class));
        PipelineJenkinsCallbackReqVO reqVO = buildReq(PipelineNodeCallbackAction.STARTED);

        // 调用
        PipelineJenkinsCallbackRespVO respVO = callbackService.handleCallback(800L, "secret", reqVO);

        // 断言
        assertTrue(respVO.getAccepted());
        assertFalse(respVO.getDuplicate());
        ArgumentCaptor<PipelineRunLogDO> logCaptor = ArgumentCaptor.forClass(PipelineRunLogDO.class);
        verify(pipelineRunLogMapper).updateById(logCaptor.capture());
        assertEquals(PipelineRunLogStatusEnum.RUNNING.getStatus(), logCaptor.getValue().getStatus());
        assertEquals(1L, logCaptor.getValue().getTenantId());
    }

    @Test
    public void testHandleCallback_completed() {
        // 准备参数
        mockBaseContext();
        PipelineRunLogDO existingLog = new PipelineRunLogDO();
        existingLog.setId(1000L);
        existingLog.setPipelineRunId(800L);
        existingLog.setNodeId("mock");
        existingLog.setStatus(PipelineRunLogStatusEnum.RUNNING.getStatus());
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("mock"))).thenReturn(existingLog);
        PipelineJenkinsCallbackReqVO reqVO = buildReq(PipelineNodeCallbackAction.COMPLETED);

        // 调用
        PipelineJenkinsCallbackRespVO respVO = callbackService.handleCallback(800L, "secret", reqVO);

        // 断言
        assertTrue(respVO.getAccepted());
        assertFalse(respVO.getDuplicate());
        ArgumentCaptor<PipelineRunLogDO> logCaptor = ArgumentCaptor.forClass(PipelineRunLogDO.class);
        verify(pipelineRunLogMapper).updateById(logCaptor.capture());
        assertEquals(PipelineRunLogStatusEnum.SUCCESS.getStatus(), logCaptor.getAllValues().get(0).getStatus());
    }

    @Test
    public void testHandleCallback_failed() {
        // 准备参数
        mockBaseContext();
        PipelineRunLogDO existingLog = new PipelineRunLogDO();
        existingLog.setId(1000L);
        existingLog.setPipelineRunId(800L);
        existingLog.setNodeId("mock");
        existingLog.setStatus(PipelineRunLogStatusEnum.RUNNING.getStatus());
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("mock"))).thenReturn(existingLog);
        PipelineJenkinsCallbackReqVO reqVO = buildReq(PipelineNodeCallbackAction.FAILED);
        reqVO.setMessage("mock failed");

        // 调用
        PipelineJenkinsCallbackRespVO respVO = callbackService.handleCallback(800L, "secret", reqVO);

        // 断言
        assertTrue(respVO.getAccepted());
        assertFalse(respVO.getDuplicate());
        ArgumentCaptor<PipelineRunDO> runCaptor = ArgumentCaptor.forClass(PipelineRunDO.class);
        verify(pipelineRunMapper, org.mockito.Mockito.atLeastOnce()).updateById(runCaptor.capture());
        assertEquals(PipelineRunStatusEnum.FAILED.getStatus(),
                runCaptor.getAllValues().get(runCaptor.getAllValues().size() - 1).getRunStatus());
    }

    @Test
    public void testHandleCallback_checkoutStarted() {
        // 准备参数
        mockBaseContext("{\"nodes\":[{\"id\":\"checkout\",\"type\":\"CHECKOUT\",\"name\":\"拉取代码\"}],\"edges\":[]}");
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("checkout"))).thenReturn(null);
        doAnswer(invocation -> {
            PipelineRunLogDO log = invocation.getArgument(0);
            log.setId(1000L);
            return 1;
        }).when(pipelineRunLogMapper).insert(any(PipelineRunLogDO.class));
        PipelineJenkinsCallbackReqVO reqVO = buildReq(PipelineNodeCallbackAction.STARTED);
        reqVO.setNodeId("checkout");
        reqVO.setNodeType(PipelineNodeRegistryServiceImpl.TYPE_CHECKOUT);
        reqVO.setNodeName("拉取代码");

        // 调用
        PipelineJenkinsCallbackRespVO respVO = callbackService.handleCallback(800L, "secret", reqVO);

        // 断言
        assertTrue(respVO.getAccepted());
        assertFalse(respVO.getDuplicate());
        ArgumentCaptor<PipelineRunLogDO> logCaptor = ArgumentCaptor.forClass(PipelineRunLogDO.class);
        verify(pipelineRunLogMapper).updateById(logCaptor.capture());
        assertEquals(PipelineRunLogStatusEnum.RUNNING.getStatus(), logCaptor.getValue().getStatus());
        assertEquals(PipelineNodeRegistryServiceImpl.TYPE_CHECKOUT, logCaptor.getValue().getNodeType());
    }

    private void mockBaseContext() {
        mockBaseContext("{\"nodes\":[{\"id\":\"mock\",\"type\":\"MOCK\",\"name\":\"Mock Node\"}],\"edges\":[]}");
    }

    private void mockBaseContext(String specJson) {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setTenantId(1L);
        run.setApplicationEnvId(100L);
        run.setDefinitionVersionId(300L);
        run.setRunStatus(PipelineRunStatusEnum.RUNNING.getStatus());
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(run);

        PipelineDefinitionVersionDO version = new PipelineDefinitionVersionDO();
        version.setId(300L);
        version.setSpecJson(specJson);
        when(pipelineDefinitionVersionMapper.selectById(eq(300L))).thenReturn(version);
    }

    private PipelineJenkinsCallbackReqVO buildReq(String action) {
        PipelineJenkinsCallbackReqVO reqVO = new PipelineJenkinsCallbackReqVO();
        reqVO.setAction(action);
        reqVO.setPipelineVersionId(300L);
        reqVO.setNodeId("mock");
        reqVO.setNodeType(PipelineNodeRegistryServiceImpl.TYPE_MOCK);
        reqVO.setNodeName("Mock Node");
        reqVO.setJenkinsBuildNumber("58");
        return reqVO;
    }

}
