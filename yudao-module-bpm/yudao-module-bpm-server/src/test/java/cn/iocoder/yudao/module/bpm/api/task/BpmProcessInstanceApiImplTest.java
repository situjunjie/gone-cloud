package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceStatusRespDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link BpmProcessInstanceApiImpl} 的单元测试。
 */
public class BpmProcessInstanceApiImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmProcessInstanceApiImpl processInstanceApi;

    @Mock
    private BpmProcessInstanceService processInstanceService;

    @Test
    public void testGetProcessInstanceStatus() {
        // 准备参数
        HistoricProcessInstance processInstance = mock(HistoricProcessInstance.class);
        when(processInstanceService.getHistoricProcessInstance(eq("pi-1"))).thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("pi-1");
        when(processInstance.getProcessDefinitionKey()).thenReturn("devops_deploy_approval");
        when(processInstance.getBusinessKey()).thenReturn("devops:pipeline-approval:800:approval");
        when(processInstance.getProcessVariables()).thenReturn(Map.of(
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS, BpmProcessInstanceStatusEnum.APPROVE.getStatus()));

        // 调用
        BpmProcessInstanceStatusRespDTO result = processInstanceApi.getProcessInstanceStatus("pi-1").getCheckedData();

        // 断言
        assertEquals("pi-1", result.getId());
        assertEquals("devops_deploy_approval", result.getProcessDefinitionKey());
        assertEquals(BpmProcessInstanceStatusEnum.APPROVE.getStatus(), result.getStatus());
        assertEquals("devops:pipeline-approval:800:approval", result.getBusinessKey());
    }

    @Test
    public void testGetProcessInstanceStatus_notFound() {
        // 准备参数
        when(processInstanceService.getHistoricProcessInstance(eq("missing"))).thenReturn(null);

        // 调用
        BpmProcessInstanceStatusRespDTO result = processInstanceApi.getProcessInstanceStatus("missing").getData();

        // 断言
        assertNull(result);
    }

}
