package cn.iocoder.yudao.module.devops.service.pipeline.plugin.dingtalk;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.service.pipeline.plugin.PipelinePlugin;
import cn.iocoder.yudao.module.devops.service.pipeline.plugin.PipelinePluginExecutionContext;
import cn.iocoder.yudao.module.devops.service.pipeline.plugin.PipelinePluginValidationIssue;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 钉钉机器人流水线通知插件。
 */
@Component
public class DingTalkPlugin implements PipelinePlugin {

    public static final String TYPE = "DingTalkPlugin";

    private static final String WEBHOOK_HOST = "oapi.dingtalk.com";
    private static final String WEBHOOK_PATH = "/robot/send";
    private static final Pattern MOBILE_PATTERN = Pattern.compile("1\\d{10}");
    private static final Set<String> NOTICE_CONTENT_FIELDS = Set.of(
            "pipelineName", "operator", "pipelineTask", "pipelineStage", "status");
    private static final Map<String, String> NOTICE_CONTENT_LABELS = Map.of(
            "pipelineName", "流水线",
            "operator", "操作人",
            "pipelineTask", "任务",
            "pipelineStage", "阶段",
            "status", "状态");

    private final DingTalkRobotClient robotClient;
    private final Clock clock;

    public DingTalkPlugin(DingTalkRobotClient robotClient) {
        this(robotClient, Clock.systemUTC());
    }

    DingTalkPlugin(DingTalkRobotClient robotClient, Clock clock) {
        this.robotClient = robotClient;
        this.clock = clock;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public List<PipelinePluginValidationIssue> validate(Map<String, Object> config) {
        Map<String, Object> safeConfig = config == null ? Map.of() : config;
        List<PipelinePluginValidationIssue> issues = new ArrayList<>();
        validateWebhook(safeConfig.get("webhook"), issues);
        validateOptionalString(safeConfig, "secret", "钉钉机器人加签密钥必须是非空字符串", issues);
        validateNoticeContent(safeConfig.get("noticeContent"), issues);
        validateOptionalString(safeConfig, "customContent", "自定义通知内容必须是字符串", issues);
        validateAt(safeConfig.get("at"), issues);
        return issues;
    }

    @Override
    public void execute(PipelinePluginExecutionContext context, Map<String, Object> config) {
        if (!validate(config).isEmpty()) {
            throw new IllegalArgumentException("钉钉机器人插件配置无效");
        }
        String webhook = String.valueOf(config.get("webhook"));
        String secret = stringValue(config.get("secret"));
        List<String> noticeContent = stringList(config.get("noticeContent"));
        List<String> atMobiles = normalizeAt(config.get("at"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "text");
        body.put("text", Map.of("content", buildContent(context, noticeContent,
                stringValue(config.get("customContent")))));
        body.put("at", Map.of("atMobiles", atMobiles, "isAtAll", false));
        robotClient.send(buildSignedWebhook(webhook, secret), body);
    }

    private void validateWebhook(Object value, List<PipelinePluginValidationIssue> issues) {
        if (!(value instanceof String webhook) || StrUtil.isBlank(webhook)) {
            issues.add(issue("webhook", "PARAM_REQUIRED", "钉钉机器人 webhook 不能为空"));
            return;
        }
        try {
            URI uri = URI.create(webhook);
            String accessToken = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("access_token");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !WEBHOOK_HOST.equalsIgnoreCase(uri.getHost())
                    || !WEBHOOK_PATH.equals(uri.getPath()) || StrUtil.isBlank(accessToken)) {
                issues.add(issue("webhook", "PARAM_VALUE_UNSUPPORTED", "钉钉机器人 webhook 地址无效"));
            }
        } catch (Exception ex) {
            issues.add(issue("webhook", "PARAM_VALUE_UNSUPPORTED", "钉钉机器人 webhook 地址无效"));
        }
    }

    private void validateOptionalString(Map<String, Object> config, String field, String message,
                                        List<PipelinePluginValidationIssue> issues) {
        if (!config.containsKey(field) || config.get(field) == null) {
            return;
        }
        Object value = config.get(field);
        if (!(value instanceof String stringValue) || ("secret".equals(field) && StrUtil.isBlank(stringValue))) {
            issues.add(issue(field, "PARAM_TYPE_INVALID", message));
        }
    }

    private void validateNoticeContent(Object value, List<PipelinePluginValidationIssue> issues) {
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            issues.add(issue("noticeContent", "PARAM_REQUIRED", "钉钉机器人通知字段不能为空"));
            return;
        }
        Set<String> fields = new LinkedHashSet<>();
        for (Object item : collection) {
            if (!(item instanceof String field) || !NOTICE_CONTENT_FIELDS.contains(field)) {
                issues.add(issue("noticeContent", "PARAM_VALUE_UNSUPPORTED", "钉钉机器人通知字段不受支持"));
                return;
            }
            if (!fields.add(field)) {
                issues.add(issue("noticeContent", "PARAM_VALUE_DUPLICATE", "钉钉机器人通知字段不能重复"));
                return;
            }
        }
    }

    private void validateAt(Object value, List<PipelinePluginValidationIssue> issues) {
        if (value == null) {
            return;
        }
        if (value instanceof String mobile) {
            if (!MOBILE_PATTERN.matcher(mobile).matches()) {
                issues.add(issue("at", "PARAM_VALUE_INVALID", "钉钉机器人 @ 手机号格式无效"));
            }
            return;
        }
        if (!(value instanceof Collection<?> collection)) {
            issues.add(issue("at", "PARAM_TYPE_INVALID", "钉钉机器人 @ 配置必须是手机号或手机号数组"));
            return;
        }
        Set<String> mobiles = new LinkedHashSet<>();
        for (Object item : collection) {
            if (!(item instanceof String mobile) || !MOBILE_PATTERN.matcher(mobile).matches()) {
                issues.add(issue("at", "PARAM_VALUE_INVALID", "钉钉机器人 @ 手机号格式无效"));
                return;
            }
            mobiles.add(mobile);
        }
        if (mobiles.size() != collection.size()) {
            issues.add(issue("at", "PARAM_VALUE_DUPLICATE", "钉钉机器人 @ 手机号不能重复"));
        }
    }

    private String buildContent(PipelinePluginExecutionContext context, List<String> fields, String customContent) {
        List<String> lines = new ArrayList<>();
        lines.add("流水线通知");
        for (String field : fields) {
            lines.add(NOTICE_CONTENT_LABELS.get(field) + "：" + contextValue(context, field));
        }
        if (StrUtil.isNotBlank(customContent)) {
            lines.add(customContent);
        }
        return String.join("\n", lines);
    }

    private String contextValue(PipelinePluginExecutionContext context, String field) {
        return switch (field) {
            case "pipelineName" -> fallback(context.getPipelineName(), context.getDefinitionId());
            case "operator" -> StrUtil.blankToDefault(context.getOperator(), "-");
            case "pipelineTask" -> fallback(context.getJobName(), context.getJobId());
            case "pipelineStage" -> fallback(context.getStageName(), context.getStageId());
            case "status" -> StrUtil.blankToDefault(context.getStatus(), "-");
            default -> "-";
        };
    }

    private String fallback(String value, Object fallback) {
        return StrUtil.blankToDefault(value, fallback == null ? "-" : String.valueOf(fallback));
    }

    private String buildSignedWebhook(String webhook, String secret) {
        if (StrUtil.isBlank(secret)) {
            return webhook;
        }
        long timestamp = clock.millis();
        String stringToSign = timestamp + "\n" + secret;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String sign = Base64.getEncoder().encodeToString(
                    mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
            return UriComponentsBuilder.fromUriString(webhook)
                    .replaceQueryParam("timestamp", timestamp)
                    .replaceQueryParam("sign", sign)
                    .build().encode().toUriString();
        } catch (Exception ex) {
            throw new IllegalStateException("钉钉机器人签名生成失败", ex);
        }
    }

    private List<String> normalizeAt(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof String mobile) {
            return List.of(mobile);
        }
        return new ArrayList<>(new LinkedHashSet<>(stringList(value)));
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream().map(String::valueOf).toList();
    }

    private String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private PipelinePluginValidationIssue issue(String field, String code, String message) {
        return new PipelinePluginValidationIssue(field, code, message);
    }

}
