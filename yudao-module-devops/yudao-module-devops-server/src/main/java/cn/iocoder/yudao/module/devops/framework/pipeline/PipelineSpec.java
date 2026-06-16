package cn.iocoder.yudao.module.devops.framework.pipeline;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DevOps 流水线配置 DSL，结构对齐流水线 YAML。
 */
@Data
public class PipelineSpec {

    /**
     * 代码源配置，key 为 sourceId。
     */
    private Map<String, Source> sources = new LinkedHashMap<>();
    /**
     * 阶段配置，key 为 stageId。
     */
    private Map<String, Stage> stages = new LinkedHashMap<>();

    public List<ExecutableStep> toExecutableSteps() {
        return toExecutableGraph().getSteps();
    }

    public List<ExecutableJob> toExecutableJobs() {
        return toExecutableGraph().getJobs();
    }

    public ExecutableGraph toExecutableGraph() {
        ExecutableGraph graph = new ExecutableGraph();
        if (stages == null || stages.isEmpty()) {
            return graph;
        }
        stages.forEach((stageId, stage) -> {
            if (stage == null || stage.getJobs() == null) {
                return;
            }
            stage.getJobs().forEach((jobId, job) -> {
                if (job == null) {
                    return;
                }
                ExecutableJob executableJob = ExecutableJob.of(stageId, stage, jobId, job);
                graph.getJobs().add(executableJob);
                graph.getJobMap().put(jobId, executableJob);
                if (job.getSteps() == null) {
                    return;
                }
                job.getSteps().forEach((stepId, step) -> {
                    if (step == null) {
                        return;
                    }
                    ExecutableStep executableStep = ExecutableStep.of(stageId, stage, jobId, job, stepId, step);
                    executableJob.getSteps().add(executableStep);
                    graph.getSteps().add(executableStep);
                });
            });
        });
        return graph;
    }

    @Data
    public static class Source {

        /**
         * 代码源类型。
         */
        private String type;
        /**
         * 代码源展示名称。
         */
        private String name;
        /**
         * 代码源地址。
         */
        private String endpoint;
        /**
         * 默认分支。
         */
        private String branch;
        /**
         * 代码源扩展参数。
         */
        private Map<String, Object> with = new LinkedHashMap<>();

    }

    @Data
    public static class Stage {

        /**
         * 阶段展示名称。
         */
        private String name;
        /**
         * 阶段是否启用。
         */
        private Boolean enabled = true;
        /**
         * 阶段下任务配置，key 为 jobId。
         */
        private Map<String, Job> jobs = new LinkedHashMap<>();

    }

    @Data
    public static class Job {

        /**
         * 任务展示名称。
         */
        private String name;
        /**
         * 任务是否启用。
         */
        private Boolean enabled = true;
        /**
         * 任务运行环境。
         */
        private RunsOn runsOn;
        /**
         * 依赖任务编号列表。
         */
        private List<String> needs = new ArrayList<>();
        /**
         * 任务下步骤配置，key 为 stepId。
         */
        private Map<String, Step> steps = new LinkedHashMap<>();
        /**
         * 任务超时时间，单位秒。
         */
        private Integer timeoutSeconds;
        /**
         * 任务重试次数。
         */
        private Integer retryTimes;
        /**
         * 任务失败策略。
         */
        private String failStrategy;

        public void setNeeds(Object needs) {
            this.needs = normalizeNeeds(needs);
        }

        private List<String> normalizeNeeds(Object needs) {
            if (needs == null) {
                return new ArrayList<>();
            }
            Set<String> normalized = new LinkedHashSet<>();
            if (needs instanceof Collection<?> collection) {
                for (Object item : collection) {
                    if (item != null) {
                        normalized.add(String.valueOf(item));
                    }
                }
                return new ArrayList<>(normalized);
            }
            normalized.add(String.valueOf(needs));
            return new ArrayList<>(normalized);
        }

    }

    @Data
    public static class RunsOn {

        /**
         * 执行资源池。
         */
        private String group;
        /**
         * 执行容器镜像。
         */
        private String container;

    }

    @Data
    public static class Step {

        /**
         * 步骤展示名称。
         */
        private String name;
        /**
         * 步骤类型。
         */
        private String step;
        /**
         * 步骤是否启用。
         */
        private Boolean enabled = true;
        /**
         * 步骤参数。
         */
        private Map<String, Object> with = new LinkedHashMap<>();
        /**
         * 步骤超时时间，单位秒。
         */
        private Integer timeoutSeconds;
        /**
         * 步骤重试次数。
         */
        private Integer retryTimes;
        /**
         * 步骤失败策略。
         */
        private String failStrategy;

    }

    @Data
    public static class ExecutableGraph {

        /**
         * 可执行任务列表。
         */
        private List<ExecutableJob> jobs = new ArrayList<>();
        /**
         * 可执行任务索引，key 为 jobId。
         */
        private Map<String, ExecutableJob> jobMap = new LinkedHashMap<>();
        /**
         * 可执行步骤扁平列表。
         */
        private List<ExecutableStep> steps = new ArrayList<>();

    }

    @Data
    public static class ExecutableJob {

        /**
         * 阶段编号。
         */
        private String stageId;
        /**
         * 阶段名称。
         */
        private String stageName;
        /**
         * 任务编号。
         */
        private String jobId;
        /**
         * 任务展示名称。
         */
        private String name;
        /**
         * 任务是否启用。
         */
        private Boolean enabled = true;
        /**
         * 任务运行环境。
         */
        private RunsOn runsOn;
        /**
         * 依赖任务编号列表。
         */
        private List<String> needs = new ArrayList<>();
        /**
         * 任务下可执行步骤。
         */
        private List<ExecutableStep> steps = new ArrayList<>();
        /**
         * 任务超时时间，单位秒。
         */
        private Integer timeoutSeconds;
        /**
         * 任务重试次数。
         */
        private Integer retryTimes;
        /**
         * 任务失败策略。
         */
        private String failStrategy;

        public static ExecutableJob of(String stageId, Stage stage, String jobId, Job job) {
            ExecutableJob executableJob = new ExecutableJob();
            executableJob.setStageId(stageId);
            executableJob.setStageName(stage.getName());
            executableJob.setJobId(jobId);
            executableJob.setName(job.getName());
            executableJob.setEnabled(!Boolean.FALSE.equals(stage.getEnabled()) && !Boolean.FALSE.equals(job.getEnabled()));
            executableJob.setRunsOn(job.getRunsOn());
            executableJob.setNeeds(job.getNeeds() == null ? new ArrayList<>() : new ArrayList<>(job.getNeeds()));
            executableJob.setTimeoutSeconds(job.getTimeoutSeconds());
            executableJob.setRetryTimes(job.getRetryTimes());
            executableJob.setFailStrategy(job.getFailStrategy());
            return executableJob;
        }

    }

    @Data
    public static class ExecutableStep {

        /**
         * 阶段编号。
         */
        private String stageId;
        /**
         * 阶段名称。
         */
        private String stageName;
        /**
         * 任务编号。
         */
        private String jobId;
        /**
         * 任务名称。
         */
        private String jobName;
        /**
         * 任务运行环境。
         */
        private RunsOn runsOn;
        /**
         * 步骤编号。
         */
        private String stepId;
        /**
         * 步骤展示名称。
         */
        private String name;
        /**
         * 步骤类型。
         */
        private String step;
        /**
         * 步骤是否启用。
         */
        private Boolean enabled = true;
        /**
         * 步骤参数。
         */
        private Map<String, Object> with = new LinkedHashMap<>();
        /**
         * 步骤超时时间，单位秒。
         */
        private Integer timeoutSeconds;
        /**
         * 步骤重试次数。
         */
        private Integer retryTimes;
        /**
         * 步骤失败策略。
         */
        private String failStrategy;

        public static ExecutableStep of(String stageId, Stage stage, String jobId, Job job,
                                        String stepId, Step step) {
            ExecutableStep executableStep = new ExecutableStep();
            executableStep.setStageId(stageId);
            executableStep.setStageName(stage.getName());
            executableStep.setJobId(jobId);
            executableStep.setJobName(job.getName());
            executableStep.setRunsOn(job.getRunsOn());
            executableStep.setStepId(stepId);
            executableStep.setName(step.getName());
            executableStep.setStep(step.getStep());
            executableStep.setEnabled(resolveEnabled(stage, job, step));
            executableStep.setWith(step.getWith() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(step.getWith()));
            executableStep.setTimeoutSeconds(step.getTimeoutSeconds() != null
                    ? step.getTimeoutSeconds() : job.getTimeoutSeconds());
            executableStep.setRetryTimes(step.getRetryTimes() != null ? step.getRetryTimes() : job.getRetryTimes());
            executableStep.setFailStrategy(step.getFailStrategy() != null
                    ? step.getFailStrategy() : job.getFailStrategy());
            return executableStep;
        }

        private static Boolean resolveEnabled(Stage stage, Job job, Step step) {
            return !Boolean.FALSE.equals(stage.getEnabled())
                    && !Boolean.FALSE.equals(job.getEnabled())
                    && !Boolean.FALSE.equals(step.getEnabled());
        }

    }

}
