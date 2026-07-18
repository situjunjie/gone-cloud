package cn.iocoder.yudao.module.devops.service.pipeline.plugin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.job.PipelineRunJobDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineRunJobStatusEnum;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionContext;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelinePluginDispatcherTest {

    private PipelineDefinitionMapper definitionMapper;
    private AdminUserApi adminUserApi;
    private CapturingPlugin plugin;

    @BeforeEach
    void setUp() {
        definitionMapper = mock(PipelineDefinitionMapper.class);
        adminUserApi = mock(AdminUserApi.class);
        plugin = new CapturingPlugin();

        PipelineDefinitionDO definition = new PipelineDefinitionDO();
        definition.setName("演示流水线");
        when(definitionMapper.selectById(10L)).thenReturn(definition);
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(7L);
        user.setNickname("张三");
        when(adminUserApi.getUser(7L)).thenReturn(CommonResult.success(user));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void testDispatchSuccessBuildsContextInTenant() {
        PipelinePluginDispatcher dispatcher = dispatcher(Runnable::run);

        dispatcher.dispatch(context(List.of(pluginConfig("success", false))),
                PipelineRunJobStatusEnum.SUCCESS.getStatus());

        assertEquals(1, plugin.contexts.size());
        assertEquals("演示流水线", plugin.contexts.get(0).getPipelineName());
        assertEquals("张三", plugin.contexts.get(0).getOperator());
        assertEquals(9L, plugin.tenantIds.get(0));
    }

    @Test
    void testDispatchStateMismatchIgnored() {
        PipelinePluginDispatcher dispatcher = dispatcher(Runnable::run);

        dispatcher.dispatch(context(List.of(pluginConfig("success", false))),
                PipelineRunJobStatusEnum.FAILED.getStatus());

        assertEquals(0, plugin.contexts.size());
    }

    @Test
    void testDispatchFailBuildsFailedContext() {
        PipelinePluginDispatcher dispatcher = dispatcher(Runnable::run);

        dispatcher.dispatch(context(List.of(pluginConfig("fail", false))),
                PipelineRunJobStatusEnum.FAILED.getStatus());

        assertEquals(1, plugin.contexts.size());
        assertEquals(PipelineRunJobStatusEnum.FAILED.getStatus(), plugin.contexts.get(0).getStatus());
    }

    @Test
    void testDispatchNonTriggerJobStatusesIgnored() {
        PipelinePluginDispatcher dispatcher = dispatcher(Runnable::run);
        PipelineExecutionContext context = context(List.of(pluginConfig("success", false),
                pluginConfig("fail", false)));

        dispatcher.dispatch(context, PipelineRunJobStatusEnum.BLOCKED.getStatus());
        dispatcher.dispatch(context, PipelineRunJobStatusEnum.SKIPPED.getStatus());
        dispatcher.dispatch(context, PipelineRunJobStatusEnum.CANCELED.getStatus());

        assertEquals(0, plugin.contexts.size());
    }

    @Test
    void testDispatchPluginFailureDoesNotStopFollowingPlugin() {
        PipelinePluginDispatcher dispatcher = dispatcher(Runnable::run);

        dispatcher.dispatch(context(List.of(pluginConfig("success", true), pluginConfig("success", false))),
                PipelineRunJobStatusEnum.SUCCESS.getStatus());

        assertEquals(2, plugin.executeCount);
        assertEquals(1, plugin.contexts.size());
    }

    @Test
    void testDispatchRejectedNoThrow() {
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("full");
        };
        PipelinePluginDispatcher dispatcher = dispatcher(rejectingExecutor);

        assertDoesNotThrow(() -> dispatcher.dispatch(context(List.of(pluginConfig("success", false))),
                PipelineRunJobStatusEnum.SUCCESS.getStatus()));
        assertEquals(0, plugin.contexts.size());
    }

    private PipelinePluginDispatcher dispatcher(Executor executor) {
        return new PipelinePluginDispatcher(new PipelinePluginRegistry(List.of(plugin)),
                definitionMapper, adminUserApi, executor);
    }

    private PipelineExecutionContext context(List<PipelineSpec.PluginConfig> plugins) {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(100L);
        run.setTenantId(9L);
        run.setDefinitionId(10L);
        run.setTriggerUserId(7L);
        PipelineSpec.ExecutableJob job = new PipelineSpec.ExecutableJob();
        job.setStageId("build_stage");
        job.setStageName("构建阶段");
        job.setJobId("build_job");
        job.setName("构建任务");
        job.setPlugins(plugins);
        PipelineRunJobDO jobRun = new PipelineRunJobDO();
        jobRun.setJobId("build_job");
        return PipelineExecutionContext.builder().run(run).job(job).jobRun(jobRun).build();
    }

    private PipelineSpec.PluginConfig pluginConfig(String triggerState, boolean shouldThrow) {
        PipelineSpec.PluginConfig config = new PipelineSpec.PluginConfig();
        config.setName("测试插件");
        config.setPlugin(plugin.type());
        config.setTriggerState(List.of(triggerState));
        Map<String, Object> with = new LinkedHashMap<>();
        with.put("shouldThrow", shouldThrow);
        config.setWith(with);
        return config;
    }

    private static class CapturingPlugin implements PipelinePlugin {
        private int executeCount;
        private final List<PipelinePluginExecutionContext> contexts = new ArrayList<>();
        private final List<Long> tenantIds = new ArrayList<>();

        @Override
        public String type() {
            return "TestPlugin";
        }

        @Override
        public List<PipelinePluginValidationIssue> validate(Map<String, Object> config) {
            return List.of();
        }

        @Override
        public void execute(PipelinePluginExecutionContext context, Map<String, Object> config) {
            executeCount++;
            if (Boolean.TRUE.equals(config.get("shouldThrow"))) {
                throw new IllegalStateException("test");
            }
            contexts.add(context);
            tenantIds.add(TenantContextHolder.getTenantId());
        }
    }

}
