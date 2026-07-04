package cn.iocoder.yudao.module.devops.service.pipeline.approval;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.job.PipelineRunJobDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.job.PipelineRunJobMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineRunJobStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.PipelineApprovalContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepAction;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepActionHelper;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepActionKeyConstants;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepLogHelper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.nio.file.Path;
import java.util.List;
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
    private PipelineRunJobMapper pipelineRunJobMapper;
    @Mock
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Mock
    private BpmProcessInstanceApi bpmProcessInstanceApi;
    @Mock
    private PipelineApprovalDeployDetailBuilder deployDetailBuilder;
    @Mock
    private PipelineStepActionHelper pipelineStepActionHelper;
    @Mock
    private PipelineStepLogHelper pipelineStepLogHelper;

    @Test
    public void testStartApproval_success() {
        // 准备参数
        PipelineRunDO run = buildRun();
        PipelineSpec.ExecutableStep step = approvalStep();
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("approval"))).thenReturn(null);
        when(bpmProcessInstanceApi.createProcessInstance(eq(7L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("pi-1"));
        when(deployDetailBuilder.buildDeployHtml(eq(run))).thenReturn("<h3>发布审批详情</h3>");
        when(pipelineRunLogMapper.insert(any(PipelineRunLogDO.class))).thenAnswer(invocation -> {
            PipelineRunLogDO log = invocation.getArgument(0);
            log.setId(900L);
            return 1;
        });

        // 调用
        PipelineApprovalExecutionStatus status = approvalService.startApproval(run, step, Path.of("/tmp/run-800"), 7L);

        // 断言
        assertEquals(PipelineApprovalExecutionStatus.SUSPEND, status);
        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> reqCaptor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(bpmProcessInstanceApi).createProcessInstance(eq(7L), reqCaptor.capture());
        assertEquals("devops_deploy_approval", reqCaptor.getValue().getProcessDefinitionKey());
        assertEquals("devops:pipeline-approval:800:approval", reqCaptor.getValue().getBusinessKey());
        assertEquals("approval", reqCaptor.getValue().getVariables().get("stepId"));
        assertEquals("发布审批", reqCaptor.getValue().getVariables().get("stepName"));
        assertEquals(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL, reqCaptor.getValue().getVariables().get("stepType"));
        assertEquals("<h3>发布审批详情</h3>", reqCaptor.getValue().getVariables().get("deploy_html"));
        assertFalse(reqCaptor.getValue().getVariables().containsKey("nodeId"));
        ArgumentCaptor<PipelineRunLogDO> logCaptor = ArgumentCaptor.forClass(PipelineRunLogDO.class);
        verify(pipelineRunLogMapper).updateById(logCaptor.capture());
        assertEquals(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL, logCaptor.getValue().getNodeType());
        assertEquals(PipelineRunLogStatusEnum.WAITING_INPUT.getStatus(), logCaptor.getValue().getStatus());
        assertEquals("/tmp/run-800", logCaptor.getValue().getWorkspacePath());
        PipelineApprovalContext context = JsonUtils.parseObject(logCaptor.getValue().getContextJson(),
                PipelineApprovalContext.class);
        assertEquals("pi-1", context.getProcessInstanceId());
        ArgumentCaptor<List<PipelineStepAction>> actionCaptor = ArgumentCaptor.captor();
        verify(pipelineStepActionHelper).upsertActions(eq(logCaptor.getValue()), actionCaptor.capture());
        assertEquals(PipelineStepActionKeyConstants.APPROVAL_OPEN_PROCESS, actionCaptor.getValue().get(0).getKey());
        assertEquals("pi-1", actionCaptor.getValue().get(0).getTarget().getQuery().get("id"));
        assertTrue(actionCaptor.getValue().get(0).getTarget().getParams().isEmpty());
        verify(pipelineStepLogHelper).appendPlatformLine(eq(logCaptor.getValue()), eq("已创建审批单：pi-1"));
    }

    @Test
    public void testStartApproval_existingSuccessReturnsSuccess() {
        // 准备参数
        PipelineRunDO run = buildRun();
        PipelineSpec.ExecutableStep step = approvalStep();
        PipelineRunLogDO log = waitingApprovalLog();
        log.setStatus(PipelineRunLogStatusEnum.SUCCESS.getStatus());
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("approval"))).thenReturn(log);

        // 调用
        PipelineApprovalExecutionStatus status = approvalService.startApproval(run, step, Path.of("/tmp/run-800"), 7L);

        // 断言
        assertEquals(PipelineApprovalExecutionStatus.SUCCESS, status);
        verify(bpmProcessInstanceApi, never()).createProcessInstance(any(), any(BpmProcessInstanceCreateReqDTO.class));
    }

    @Test
    public void testHandleProcessInstanceStatus_approve() {
        // 准备参数
        PipelineRunDO run = buildRun();
        PipelineRunLogDO log = waitingApprovalLog();
        PipelineRunJobDO job = blockedApprovalJob();
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(run);
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("approval"))).thenReturn(log);
        when(pipelineRunJobMapper.selectByPipelineRunIdAndJobId(eq(800L), eq("approval_job"))).thenReturn(job);

        // 调用
        PipelineApprovalStatusHandleResult result = approvalService.handleProcessInstanceStatus(
                event(BpmProcessInstanceStatusEnum.APPROVE.getStatus(), null));

        // 断言
        assertTrue(result.getHandled());
        assertTrue(result.getApproved());
        ArgumentCaptor<PipelineRunLogDO> logCaptor = ArgumentCaptor.forClass(PipelineRunLogDO.class);
        verify(pipelineRunLogMapper).updateById(logCaptor.capture());
        assertEquals(PipelineRunLogStatusEnum.SUCCESS.getStatus(), logCaptor.getValue().getStatus());
        verify(pipelineStepActionHelper).removeActions(eq(logCaptor.getValue()),
                eq(PipelineStepActionKeyConstants.APPROVAL_OPEN_PROCESS));
        assertEquals(PipelineRunJobStatusEnum.PENDING.getStatus(), job.getStatus());
        assertEquals("审批通过，等待继续执行", job.getSummary());
        verify(pipelineRunJobMapper).updateById(eq(job));
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
        verify(pipelineStepActionHelper).removeActions(eq(logCaptor.getValue()),
                eq(PipelineStepActionKeyConstants.APPROVAL_OPEN_PROCESS));
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

    private PipelineSpec.ExecutableStep approvalStep() {
        PipelineSpec.ExecutableStep step = new PipelineSpec.ExecutableStep();
        step.setStepId("approval");
        step.setStep(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL);
        step.setName("发布审批");
        step.setWith(Map.of("processDefinitionKey", "devops_deploy_approval"));
        return step;
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
        log.setJobId("approval_job");
        log.setStatus(PipelineRunLogStatusEnum.WAITING_INPUT.getStatus());
        log.setContextJson(JsonUtils.toJsonString(context));
        return log;
    }

    private PipelineRunJobDO blockedApprovalJob() {
        PipelineRunJobDO job = new PipelineRunJobDO();
        job.setId(901L);
        job.setPipelineRunId(800L);
        job.setJobId("approval_job");
        job.setStatus(PipelineRunJobStatusEnum.BLOCKED.getStatus());
        job.setSummary("等待审批");
        return job;
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
