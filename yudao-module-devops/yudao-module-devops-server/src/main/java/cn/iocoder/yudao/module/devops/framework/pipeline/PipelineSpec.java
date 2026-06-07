package cn.iocoder.yudao.module.devops.framework.pipeline;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DevOps 流水线后端执行 DSL。
 */
@Data
public class PipelineSpec {

    private String dslVersion;
    private String executionMode;
    private List<Node> nodes = new ArrayList<>();
    private List<Edge> edges = new ArrayList<>();

    @Data
    public static class Node {

        private String id;
        private String type;
        private String name;
        private Boolean enabled = true;
        private Map<String, Object> params = new HashMap<>();
        private Integer timeoutSeconds;
        private Integer retryTimes;
        private String failStrategy;

    }

    @Data
    public static class Edge {

        private String source;
        private String target;

    }

}
