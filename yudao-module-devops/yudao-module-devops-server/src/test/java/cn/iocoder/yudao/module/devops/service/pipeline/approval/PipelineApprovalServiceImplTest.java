package cn.iocoder.yudao.module.devops.service.pipeline.approval;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.PipelineApprovalContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PipelineApprovalServiceImpl} 的单元测试。
 */
public class PipelineApprovalServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private PipelineApprovalServiceImpl approvalService;

    @Mock
    private PipelineRunMapper pipelineRunMapper;
    @Mock
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Mock
    private BpmProcessInstanceApi bpmProcessInstanceApi;

    @Test
    public void testStartApproval_success() {
        // 准备参数
        PipelineRunDO run = buildRun();
        PipelineSpec.Node node = approvalNode();
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("approval"))).thenReturn(null);
        when(bpmProcessInstanceApi.createProcessInstance(eq(7L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("pi-1"));
        when(pipelineRunLogMapper.insert(any(PipelineRunLogDO.class))).thenAnswer(invocation -> {
            PipelineRunLogDO log = invocation.getArgument(0);
            log.setId(900L);
            return 1;
        });

        // 调用
        approvalService.startApproval(run, node, 7L);

        // 断言
        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> reqCaptor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(bpmProcessInstanceApi).createProcessInstance(eq(7L), reqCaptor.capture());
        assertEquals("devops_deploy_approval", reqCaptor.getValue().getProcessDefinitionKey());
        assertEquals("devops:pipeline-approval:800:approval", reqCaptor.getValue().getBusinessKey());
        ArgumentCaptor<PipelineRunLogDO> logCaptor = ArgumentCaptor.forClass(PipelineRunLogDO.class);
        verify(pipelineRunLogMapper).updateById(logCaptor.capture());
        assertEquals(PipelineRunLogStatusEnum.WAITING_INPUT.getStatus(), logCaptor.getValue().getStatus());
        PipelineApprovalContext context = JsonUtils.parseObject(logCaptor.getValue().getContextJson(),
                PipelineApprovalContext.class);
        assertEquals("pi-1", context.getProcessInstanceId());
    }

    @Test
    public void testHandleProcessInstanceStatus_approve() {
        // 准备参数
        PipelineRunDO run = buildRun();
        PipelineRunLogDO log = waitingApprovalLog();
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(run);
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("approval"))).thenReturn(log);

        // 调用
        PipelineApprovalStatusHandleResult result = approvalService.handleProcessInstanceStatus(
                event(BpmProcessInstanceStatusEnum.APPROVE.getStatus(), null));

        // 断言
        assertTrue(result.getHandled());
        assertTrue(result.getApproved());
        ArgumentCaptor<PipelineRunLogDO> logCaptor = ArgumentCaptor.forClass(PipelineRunLogDO.class);
        verify(pipelineRunLogMapper).updateById(logCaptor.capture());
        assertEquals(PipelineRunLogStatusEnum.SUCCESS.getStatus(), logCaptor.getValue().getStatus());
    }

    @Test
    public void testHandleProcessInstanceStatus_duplicateApproveDoesNotAdvance() {
        // 准备参数
        PipelineRunDO run = buildRun();
        PipelineRunLogDO log = waitingApprovalLog();
        log.setStatus(PipelineRunLogStatusEnum.SUCCESS.getStatus());
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(run);
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("approval"))).thenReturn(log);

        // 调用
        PipelineApprovalStatusHandleResult result = approvalService.handleProcessInstanceStatus(
                event(BpmProcessInstanceStatusEnum.APPROVE.getStatus(), null));

        // 断言
        assertTrue(result.getHandled());
        assertFalse(result.getApproved());
        verify(pipelineRunLogMapper, never()).updateById(any(PipelineRunLogDO.class));
    }

    @Test
    public void testHandleProcessInstanceStatus_reject() {
        // 准备参数
        PipelineRunDO run = buildRun();
        PipelineRunLogDO log = waitingApprovalLog();
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(run);
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("approval"))).thenReturn(log);

        // 调用
        PipelineApprovalStatusHandleResult result = approvalService.handleProcessInstanceStatus(
                event(BpmProcessInstanceStatusEnum.REJECT.getStatus(), "不允许发布"));

        // 断言
        assertTrue(result.getHandled());
        ArgumentCaptor<PipelineRunLogDO> logCaptor = ArgumentCaptor.forClass(PipelineRunLogDO.class);
        verify(pipelineRunLogMapper).updateById(logCaptor.capture());
        assertEquals(PipelineRunLogStatusEnum.FAILED.getStatus(), logCaptor.getValue().getStatus());
        ArgumentCaptor<PipelineRunDO> runCaptor = ArgumentCaptor.forClass(PipelineRunDO.class);
        verify(pipelineRunMapper).updateById(runCaptor.capture());
        assertEquals(PipelineRunStatusEnum.FAILED.getStatus(), runCaptor.getValue().getRunStatus());
    }

    private PipelineRunDO buildRun() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setTenantId(1L);
        run.setDefinitionId(200L);
        run.setDefinitionVersionId(300L);
        run.setAppId(1L);
        run.setApplicationEnvId(100L);
        run.setBranchName("release/test/20260611120000");
        run.setTriggerUserId(7L);
        return run;
    }

    private PipelineSpec.Node approvalNode() {
        PipelineSpec.Node node = new PipelineSpec.Node();
        node.setId("approval");
        node.setType(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL);
        node.setName("发布审批");
        node.setParams(Map.of("processDefinitionKey", "devops_deploy_approval"));
        return node;
    }

    private PipelineRunLogDO waitingApprovalLog() {
        PipelineApprovalContext context = new PipelineApprovalContext();
        context.setProcessDefinitionKey("devops_deploy_approval");
        context.setProcessInstanceId("pi-1");
        context.setBusinessKey("devops:pipeline-approval:800:approval");
        context.setStatus(PipelineApprovalContext.STATUS_WAITING);
        PipelineRunLogDO log = new PipelineRunLogDO();
        log.setId(900L);
        log.setPipelineRunId(800L);
        log.setNodeId("approval");
        log.setStatus(PipelineRunLogStatusEnum.WAITING_INPUT.getStatus());
        log.setContextJson(JsonUtils.toJsonString(context));
        return log;
    }

    private BpmProcessInstanceStatusEvent event(Integer status, String reason) {
        BpmProcessInstanceStatusEvent event = new BpmProcessInstanceStatusEvent();
        event.setId("pi-1");
        event.setProcessDefinitionKey("devops_deploy_approval");
        event.setBusinessKey("devops:pipeline-approval:800:approval");
        event.setStatus(status);
        event.setReason(reason);
        return event;
    }

}
