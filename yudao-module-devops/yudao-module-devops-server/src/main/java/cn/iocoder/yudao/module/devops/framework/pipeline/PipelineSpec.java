package cn.iocoder.yudao.module.devops.framework.pipeline;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DevOps 流水线配置 DSL，结构对齐流水线 YAML。
 */
@Data
public class PipelineSpec {

    private Map<String, Source> sources = new LinkedHashMap<>();
    private Map<String, Stage> stages = new LinkedHashMap<>();

    /**
     * 将 stages.jobs.steps 展开为执行引擎内部节点。
     */
    public List<Node> toExecutableNodes() {
        List<Node> result = new ArrayList<>();
        if (stages == null || stages.isEmpty()) {
            return result;
        }
        stages.forEach((stageId, stage) -> {
            if (stage == null || stage.getJobs() == null) {
                return;
            }
            stage.getJobs().forEach((jobId, job) -> {
                if (job == null || job.getSteps() == null) {
                    return;
                }
                job.getSteps().forEach((stepId, step) -> {
                    if (step == null) {
                        return;
                    }
                    result.add(step.toNode(stageId, jobId, stepId));
                });
            });
        });
        return result;
    }

    @Data
    public static class Source {

        private String type;
        private String name;
        private String endpoint;
        private String branch;
        private Map<String, Object> with = new LinkedHashMap<>();

    }

    @Data
    public static class Stage {

        private String name;
        private Boolean enabled = true;
        private Map<String, Job> jobs = new LinkedHashMap<>();

    }

    @Data
    public static class Job {

        private String name;
        private Boolean enabled = true;
        private RunsOn runsOn = new RunsOn();
        private Map<String, Step> steps = new LinkedHashMap<>();
        private Integer timeoutSeconds;
        private Integer retryTimes;
        private String failStrategy;

    }

    @Data
    public static class RunsOn {

        private String group;
        private String container;

    }

    @Data
    public static class Step {

        private String name;
        private String step;
        private Boolean enabled = true;
        private Map<String, Object> with = new LinkedHashMap<>();
        private Integer timeoutSeconds;
        private Integer retryTimes;
        private String failStrategy;

        public Node toNode(String stageId, String jobId, String stepId) {
            Node node = new Node();
            node.setId(stepId);
            node.setType(step);
            node.setName(name);
            node.setEnabled(enabled);
            node.setParams(with == null ? new LinkedHashMap<>() : new LinkedHashMap<>(with));
            node.setTimeoutSeconds(timeoutSeconds);
            node.setRetryTimes(retryTimes);
            node.setFailStrategy(failStrategy);
            node.setStageId(stageId);
            node.setJobId(jobId);
            return node;
        }

    }

    @Data
    public static class Node {

        private String id;
        private String type;
        private String name;
        private Boolean enabled = true;
        private Map<String, Object> params = new LinkedHashMap<>();
        private Integer timeoutSeconds;
        private Integer retryTimes;
        private String failStrategy;
        private String stageId;
        private String jobId;

    }

}
