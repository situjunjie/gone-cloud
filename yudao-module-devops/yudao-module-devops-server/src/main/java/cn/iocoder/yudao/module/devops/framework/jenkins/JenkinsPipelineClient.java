package cn.iocoder.yudao.module.devops.framework.jenkins;

import java.util.List;

public interface JenkinsPipelineClient {

    boolean isEnabled();

    List<JenkinsToolInstallation> getToolInstallations(String type);

    JenkinsPipelineStartResult startPipeline(JenkinsPipelineStartRequest request);

    void stopPipeline(String jobName, String buildNumber);

    JenkinsConsoleChunk getConsoleText(String buildNumber, Long start);

}
