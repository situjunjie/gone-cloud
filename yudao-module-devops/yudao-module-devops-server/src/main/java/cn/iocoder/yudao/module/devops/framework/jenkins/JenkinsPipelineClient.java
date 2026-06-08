package cn.iocoder.yudao.module.devops.framework.jenkins;

public interface JenkinsPipelineClient {

    boolean isEnabled();

    JenkinsPipelineStartResult startPipeline(JenkinsPipelineStartRequest request);

    void stopPipeline(String jobName, String buildNumber);

}
