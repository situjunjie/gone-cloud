package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.approval.PipelineApprovalExecutionStatus;
import cn.iocoder.yudao.module.devops.service.pipeline.approval.PipelineApprovalService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link ApprovalNodeHandler} 的单元测试。
 */
public class ApprovalNodeHandlerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ApprovalNodeHandler handler;

    @Mock
    private PipelineApprovalService pipelineApprovalService;

    @Test
    public void testSupports() {
        assertTrue(handler.supports(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL));
        assertFalse(handler.supports(PipelineNodeRegistryServiceImpl.TYPE_EXECUTE_SHELL));
    }

    @Test
    public void testHandle_suspend() {
        // 准备参数
        PipelineRunDO run = buildRun();
        PipelineSpec.Node node = buildNode();
        PipelineNodeContext context = buildContext(run, node);
        when(pipelineApprovalService.startApproval(eq(run), eq(node), eq(7L)))
                .thenReturn(PipelineApprovalExecutionStatus.SUSPEND);

        // 调用
        NodeOutcome outcome = handler.handle(context);

        // 断言
        assertEquals(NodeOutcome.SUSPEND, outcome);
    }

    @Test
    public void testHandle_success() {
        // 准备参数
        PipelineRunDO run = buildRun();
        PipelineSpec.Node node = buildNode();
        PipelineNodeContext context = buildContext(run, node);
        when(pipelineApprovalService.startApproval(eq(run), eq(node), eq(7L)))
                .thenReturn(PipelineApprovalExecutionStatus.SUCCESS);

        // 调用
        NodeOutcome outcome = handler.handle(context);

        // 断言
        assertEquals(NodeOutcome.CONTINUE, outcome);
    }

    private PipelineNodeContext buildContext(PipelineRunDO run, PipelineSpec.Node node) {
        return PipelineNodeContext.builder()
                .run(run)
                .node(node)
                .sharedState(new ConcurrentHashMap<>())
                .userId(7L)
                .build();
    }

    private PipelineRunDO buildRun() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setTenantId(1L);
        run.setTriggerUserId(7L);
        return run;
    }

    private PipelineSpec.Node buildNode() {
        PipelineSpec.Node node = new PipelineSpec.Node();
        node.setId("approval");
        node.setType(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL);
        node.setName("发布审批");
        node.setParams(Map.of("processDefinitionKey", "devops_deploy_approval"));
        return node;
    }

}
