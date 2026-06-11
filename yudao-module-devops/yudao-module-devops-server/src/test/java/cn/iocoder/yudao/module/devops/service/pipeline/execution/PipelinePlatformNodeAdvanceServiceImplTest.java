package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionVersionMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.deployment.DeploymentOrderService;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineSpecValidationService;
import cn.iocoder.yudao.module.devops.service.pipeline.approval.PipelineApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PipelinePlatformNodeAdvanceServiceImpl} 的单元测试。
 */
public class PipelinePlatformNodeAdvanceServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private PipelinePlatformNodeAdvanceServiceImpl advanceService;

    @Mock
    private PipelineDefinitionVersionMapper pipelineDefinitionVersionMapper;
    @Mock
    private PipelineRunMapper pipelineRunMapper;
    @Mock
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Mock
    private PipelineSpecValidationService pipelineSpecValidationService;
    @Mock
    private PipelineApprovalService pipelineApprovalService;
    @Mock
    private DeploymentOrderService deploymentOrderService;

    @BeforeEach
    public void setUp() {
        when(pipelineSpecValidationService.sortNodes(any())).thenAnswer(invocation -> {
            PipelineSpec spec = invocation.getArgument(0);
            return spec.getNodes();
        });
    }

    @Test
    public void testAdvance_startApprovalBeforeDeploy() {
        // 准备参数
        PipelineRunDO run = buildRun();
        PipelineDefinitionVersionDO version = buildVersion(spec(List.of(
                node("docker", PipelineNodeRegistryServiceImpl.TYPE_DOCKER_BUILD_PUSH, Map.of()),
                node("approval", PipelineNodeRegistryServiceImpl.TYPE_APPROVAL,
                        Map.of("processDefinitionKey", "devops_deploy_approval")),
                node("deploy", PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY, Map.of())
        )));

        // 调用
        advanceService.advance(run, version);

        // 断言
        verify(pipelineApprovalService).startApproval(eq(run), any(PipelineSpec.Node.class), eq(7L));
        verify(deploymentOrderService, never()).startContainerDeploy(any(), any(), any());
    }

    @Test
    public void testAdvance_approvalSuccessStartsDeploy() {
        // 准备参数
        PipelineRunDO run = buildRun();
        PipelineDefinitionVersionDO version = buildVersion(spec(List.of(
                node("approval", PipelineNodeRegistryServiceImpl.TYPE_APPROVAL,
                        Map.of("processDefinitionKey", "devops_deploy_approval")),
                node("deploy", PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY, Map.of())
        )));
        PipelineRunLogDO approvalLog = new PipelineRunLogDO();
        approvalLog.setStatus(PipelineRunLogStatusEnum.SUCCESS.getStatus());
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("approval"))).thenReturn(approvalLog);

        // 调用
        advanceService.advance(run, version);

        // 断言
        verify(deploymentOrderService).startContainerDeploy(eq(run), any(PipelineSpec.Node.class), eq(7L));
    }

    @Test
    public void testAdvance_noPlatformNodeMarksRunSuccess() {
        // 准备参数
        PipelineRunDO run = buildRun();
        PipelineDefinitionVersionDO version = buildVersion(spec(List.of(
                node("docker", PipelineNodeRegistryServiceImpl.TYPE_DOCKER_BUILD_PUSH, Map.of())
        )));

        // 调用
        advanceService.advance(run, version);

        // 断言
        ArgumentCaptor<PipelineRunDO> runCaptor = ArgumentCaptor.forClass(PipelineRunDO.class);
        verify(pipelineRunMapper).updateById(runCaptor.capture());
        assertEquals(PipelineRunStatusEnum.SUCCESS.getStatus(), runCaptor.getValue().getRunStatus());
    }

    private PipelineRunDO buildRun() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setDefinitionVersionId(300L);
        run.setTriggerUserId(7L);
        return run;
    }

    private PipelineDefinitionVersionDO buildVersion(PipelineSpec spec) {
        PipelineDefinitionVersionDO version = new PipelineDefinitionVersionDO();
        version.setId(300L);
        version.setSpecJson(JsonUtils.toJsonString(spec));
        return version;
    }

    private PipelineSpec spec(List<PipelineSpec.Node> nodes) {
        PipelineSpec spec = new PipelineSpec();
        spec.setNodes(new ArrayList<>(nodes));
        return spec;
    }

    private PipelineSpec.Node node(String id, String type, Map<String, Object> params) {
        PipelineSpec.Node node = new PipelineSpec.Node();
        node.setId(id);
        node.setType(type);
        node.setName(id);
        node.setParams(params);
        return node;
    }

}
