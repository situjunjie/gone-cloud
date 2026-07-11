package cn.iocoder.yudao.module.devops.service.deployment.context;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部署单当前执行结果快照。
 */
@Data
public class DeploymentOrderResultContext {

    public static final String SCHEMA_VERSION = "1.0";

    private String schemaVersion = SCHEMA_VERSION;
    private String stage;
    private String summary;
    private List<ResourceApplyResult> applyResults = new ArrayList<>();
    private Map<String, Object> rollout = new LinkedHashMap<>();
    private Map<String, Object> replicaSet = new LinkedHashMap<>();
    private List<Map<String, Object>> pods = new ArrayList<>();
    private List<Map<String, Object>> services = new ArrayList<>();
    private Map<String, Object> health = new LinkedHashMap<>();
    private List<Map<String, Object>> events = new ArrayList<>();
    private Map<String, Object> failure;
    private LocalDateTime capturedAt;

    @Data
    public static class ResourceApplyResult {

        private Integer documentIndex;
        private String operation;
        private String apiVersion;
        private String kind;
        private String namespace;
        private String name;
        private String status;
        private String uid;
        private String resourceVersion;
        private Long generation;
        private String message;

    }

}
