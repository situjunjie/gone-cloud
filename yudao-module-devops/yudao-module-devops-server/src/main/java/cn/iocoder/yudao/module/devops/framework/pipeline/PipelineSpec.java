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

    public List<ExecutableStep> toExecutableSteps() {
        List<ExecutableStep> result = new ArrayList<>();
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
                    result.add(ExecutableStep.of(stageId, stage, jobId, job, stepId, step));
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

    }

    @Data
    public static class ExecutableStep {

        private String stageId;
        private String stageName;
        private String jobId;
        private String jobName;
        private RunsOn runsOn;
        private String stepId;
        private String name;
        private String step;
        private Boolean enabled = true;
        private Map<String, Object> with = new LinkedHashMap<>();
        private Integer timeoutSeconds;
        private Integer retryTimes;
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
