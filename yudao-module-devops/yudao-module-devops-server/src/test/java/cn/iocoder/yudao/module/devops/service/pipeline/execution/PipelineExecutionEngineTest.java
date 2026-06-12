package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionVersionMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineSpecValidationService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.NodeOutcome;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineNodeContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineNodeHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link PipelineExecutionEngine} 单元测试。
 */
class PipelineExecutionEngineTest {

    @Mock
    private PipelineDefinitionVersionMapper pipelineDefinitionVersionMapper;
    @Mock
    private PipelineRunMapper pipelineRunMapper;
    @Mock
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Mock
    private PipelineSpecValidationService pipelineSpecValidationService;

    @InjectMocks
    private PipelineExecutionEngine engine;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /**
     * 测试简单的 CONTINUE 流转:两个 Mock 节点均成功,run 标记为 SUCCESS。
     */
    @Test
    void testExecute_allNodesContinue_success() {
        // 准备数据
        PipelineRunDO run = new PipelineRunDO();
        run.setId(1L);
        run.setDefinitionVersionId(100L);

        PipelineDefinitionVersionDO version = new PipelineDefinitionVersionDO();
        version.setId(100L);

        PipelineSpec spec = new PipelineSpec();
        PipelineSpec.Node node1 = new PipelineSpec.Node();
        node1.setId("test-1");
        node1.setType("TEST_NODE_SUCCESS");
        node1.setName("测试节点1");
        node1.setEnabled(true);

        PipelineSpec.Node node2 = new PipelineSpec.Node();
        node2.setId("test-2");
        node2.setType("TEST_NODE_SUCCESS");
        node2.setName("测试节点2");
        node2.setEnabled(true);

        spec.setNodes(List.of(node1, node2));
        version.setSpecJson(JsonUtils.toJsonString(spec));

        // Mock handler 总是返回 CONTINUE
        PipelineNodeHandler mockHandler = mock(PipelineNodeHandler.class);
        when(mockHandler.supports("TEST_NODE_SUCCESS")).thenReturn(true);
        when(mockHandler.handle(any())).thenReturn(NodeOutcome.CONTINUE);

        // 使用反射注入 handlers
        try {
            var field = PipelineExecutionEngine.class.getDeclaredField("handlers");
            field.setAccessible(true);
            field.set(engine, List.of(mockHandler));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Mock 依赖
        when(pipelineDefinitionVersionMapper.selectById(100L)).thenReturn(version);
        when(pipelineSpecValidationService.sortNodes(any())).thenReturn(List.of(node1, node2));

        // 执行
        engine.execute(run, 999L);

        // 验证:handler 被调用两次
        verify(mockHandler, times(2)).handle(any());

        // 验证:run 被标记为 SUCCESS
        ArgumentCaptor<PipelineRunDO> captor = ArgumentCaptor.forClass(PipelineRunDO.class);
        verify(pipelineRunMapper, times(1)).updateById(captor.capture());
        PipelineRunDO updated = captor.getValue();
        assertEquals(1L, updated.getId());
        assertEquals(PipelineRunStatusEnum.SUCCESS.getStatus(), updated.getRunStatus());
        assertNotNull(updated.getFinishedAt());
    }

    /**
     * 测试 FAIL 流转:第二个节点失败,run 标记为 FAILED。
     */
    @Test
    void testExecute_nodeFailure_runFailed() {
        // 准备数据
        PipelineRunDO run = new PipelineRunDO();
        run.setId(2L);
        run.setDefinitionVersionId(200L);

        PipelineDefinitionVersionDO version = new PipelineDefinitionVersionDO();
        version.setId(200L);

        PipelineSpec spec = new PipelineSpec();
        PipelineSpec.Node node1 = new PipelineSpec.Node();
        node1.setId("test-1");
        node1.setType("TEST_NODE");
        node1.setEnabled(true);

        PipelineSpec.Node node2 = new PipelineSpec.Node();
        node2.setId("test-2");
        node2.setType("TEST_NODE");
        node2.setEnabled(true);

        spec.setNodes(List.of(node1, node2));
        version.setSpecJson(JsonUtils.toJsonString(spec));

        // Mock handler:第一次 CONTINUE,第二次 FAIL
        PipelineNodeHandler mockHandler = mock(PipelineNodeHandler.class);
        when(mockHandler.supports("TEST_NODE")).thenReturn(true);
        when(mockHandler.handle(any())).thenReturn(NodeOutcome.CONTINUE, NodeOutcome.FAIL);

        // 使用反射注入 handlers
        try {
            var field = PipelineExecutionEngine.class.getDeclaredField("handlers");
            field.setAccessible(true);
            field.set(engine, List.of(mockHandler));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Mock 依赖
        when(pipelineDefinitionVersionMapper.selectById(200L)).thenReturn(version);
        when(pipelineSpecValidationService.sortNodes(any())).thenReturn(List.of(node1, node2));

        // 执行
        engine.execute(run, 999L);

        // 验证:handler 被调用两次(第二次失败后停止)
        verify(mockHandler, times(2)).handle(any());

        // 验证:run 被标记为 FAILED
        ArgumentCaptor<PipelineRunDO> captor = ArgumentCaptor.forClass(PipelineRunDO.class);
        verify(pipelineRunMapper, times(1)).updateById(captor.capture());
        PipelineRunDO updated = captor.getValue();
        assertEquals(2L, updated.getId());
        assertEquals(PipelineRunStatusEnum.FAILED.getStatus(), updated.getRunStatus());
        assertNotNull(updated.getFinishedAt());
        assertNotNull(updated.getErrorMessage());
    }

    /**
     * 测试 SUSPEND 流转:节点挂起,引擎返回不继续执行。
     */
    @Test
    void testExecute_nodeSuspend_engineStops() {
        // 准备数据
        PipelineRunDO run = new PipelineRunDO();
        run.setId(3L);
        run.setDefinitionVersionId(300L);

        PipelineDefinitionVersionDO version = new PipelineDefinitionVersionDO();
        version.setId(300L);

        PipelineSpec spec = new PipelineSpec();
        PipelineSpec.Node node1 = new PipelineSpec.Node();
        node1.setId("test-1");
        node1.setType("TEST_NODE");
        node1.setEnabled(true);

        PipelineSpec.Node node2 = new PipelineSpec.Node();
        node2.setId("test-2");
        node2.setType("TEST_NODE");
        node2.setEnabled(true);

        spec.setNodes(List.of(node1, node2));
        version.setSpecJson(JsonUtils.toJsonString(spec));

        // Mock handler:第一次 SUSPEND
        PipelineNodeHandler mockHandler = mock(PipelineNodeHandler.class);
        when(mockHandler.supports("TEST_NODE")).thenReturn(true);
        when(mockHandler.handle(any())).thenReturn(NodeOutcome.SUSPEND);

        // 使用反射注入 handlers
        try {
            var field = PipelineExecutionEngine.class.getDeclaredField("handlers");
            field.setAccessible(true);
            field.set(engine, List.of(mockHandler));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Mock 依赖
        when(pipelineDefinitionVersionMapper.selectById(300L)).thenReturn(version);
        when(pipelineSpecValidationService.sortNodes(any())).thenReturn(List.of(node1, node2));

        // 执行
        engine.execute(run, 999L);

        // 验证:handler 只被调用一次(SUSPEND 后停止)
        verify(mockHandler, times(1)).handle(any());

        // 验证:run 不被更新(挂起状态,等待外部事件)
        verify(pipelineRunMapper, never()).updateById(any(PipelineRunDO.class));
    }

}
