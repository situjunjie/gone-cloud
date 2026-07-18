package cn.iocoder.yudao.module.devops.service.pipeline.plugin.dingtalk;

import cn.iocoder.yudao.module.devops.service.pipeline.plugin.PipelinePluginExecutionContext;
import cn.iocoder.yudao.module.devops.service.pipeline.plugin.PipelinePluginRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DingTalkPluginTest {

    private static final String WEBHOOK =
            "https://oapi.dingtalk.com/robot/send?access_token=test-token";

    private CapturingRobotClient robotClient;
    private DingTalkPlugin plugin;

    @BeforeEach
    void setUp() {
        robotClient = new CapturingRobotClient();
        plugin = new DingTalkPlugin(robotClient,
                Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC));
    }

    @Test
    void testExecute_buildTextAndAtMobiles() {
        Map<String, Object> config = validConfig();
        config.put("noticeContent", List.of("pipelineName", "pipelineTask", "status"));
        config.put("customContent", "发布 ${COMMIT_SHA}");
        config.put("at", List.of("13800000001", "13800000002"));

        plugin.execute(context(), config);

        assertEquals(WEBHOOK, robotClient.url);
        assertEquals("text", robotClient.body.get("msgtype"));
        @SuppressWarnings("unchecked")
        Map<String, Object> text = (Map<String, Object>) robotClient.body.get("text");
        String content = String.valueOf(text.get("content"));
        assertEquals("流水线通知\n流水线：演示流水线\n任务：构建任务\n状态：SUCCESS\n发布 ${COMMIT_SHA}", content);
        assertFalse(content.contains("操作人"));
        @SuppressWarnings("unchecked")
        Map<String, Object> at = (Map<String, Object>) robotClient.body.get("at");
        assertEquals(List.of("13800000001", "13800000002"), at.get("atMobiles"));
        assertEquals(false, at.get("isAtAll"));
    }

    @Test
    void testExecute_scalarAtNormalized() {
        Map<String, Object> config = validConfig();
        config.put("at", "13800000001");

        plugin.execute(context(), config);

        @SuppressWarnings("unchecked")
        Map<String, Object> at = (Map<String, Object>) robotClient.body.get("at");
        assertEquals(List.of("13800000001"), at.get("atMobiles"));
    }

    @Test
    void testExecute_withSecretBuildSignedWebhook() {
        Map<String, Object> config = validConfig();
        config.put("secret", "SEC-test");

        plugin.execute(context(), config);

        var queryParams = UriComponentsBuilder.fromUriString(robotClient.url).build().getQueryParams();
        assertEquals("test-token", queryParams.getFirst("access_token"));
        assertEquals("1700000000000", queryParams.getFirst("timestamp"));
        assertEquals("Sf/ft2shUSqoR1REF+DS39IoTlwQRyDPwfM8S94ODOc=",
                UriUtils.decode(queryParams.getFirst("sign"), StandardCharsets.UTF_8));
        assertFalse(robotClient.url.contains("SEC-test"));
    }

    @Test
    void testSpringContext_createPluginWithRobotClient() {
        CapturingRobotClient contextRobotClient = new CapturingRobotClient();
        try (AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext()) {
            applicationContext.registerBean(DingTalkRobotClient.class, () -> contextRobotClient);
            applicationContext.register(DingTalkPlugin.class, PipelinePluginRegistry.class);
            applicationContext.refresh();

            DingTalkPlugin contextPlugin = applicationContext.getBean(DingTalkPlugin.class);
            PipelinePluginRegistry pluginRegistry = applicationContext.getBean(PipelinePluginRegistry.class);
            assertSame(contextPlugin, pluginRegistry.resolve(DingTalkPlugin.TYPE));
            contextPlugin.execute(context(), validConfig());
        }

        assertEquals(WEBHOOK, contextRobotClient.url);
    }

    @Test
    void testValidate_rejectUnsafeAndInvalidParams() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("webhook", "http://127.0.0.1/internal?access_token=secret-token");
        config.put("secret", "");
        config.put("noticeContent", List.of("unknown"));
        config.put("at", List.of("invalid"));

        var issues = plugin.validate(config);

        assertEquals(4, issues.size());
        assertTrue(issues.stream().noneMatch(issue -> issue.getMessage().contains("secret-token")));
    }

    private Map<String, Object> validConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("webhook", WEBHOOK);
        config.put("noticeContent", List.of("pipelineName", "operator", "pipelineTask", "pipelineStage", "status"));
        return config;
    }

    private PipelinePluginExecutionContext context() {
        return PipelinePluginExecutionContext.builder()
                .tenantId(9L)
                .runId(100L)
                .definitionId(10L)
                .pipelineName("演示流水线")
                .triggerUserId(7L)
                .operator("张三")
                .stageId("build_stage")
                .stageName("构建阶段")
                .jobId("build_job")
                .jobName("构建任务")
                .status("SUCCESS")
                .build();
    }

    private static class CapturingRobotClient implements DingTalkRobotClient {
        private String url;
        private Map<String, Object> body;

        @Override
        public void send(String webhookUrl, Map<String, Object> requestBody) {
            this.url = webhookUrl;
            this.body = requestBody;
        }
    }

}
