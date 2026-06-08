package cn.iocoder.yudao.module.devops.framework.jenkins;

import lombok.Data;

/**
 * Jenkins 流水线启动请求。
 */
@Data
public class JenkinsPipelineStartRequest {

    private Long pipelineRunId;
    private Long pipelineVersionId;
    private String repoUrl;
    private String branchName;
    private String commitSha;
    private String appKey;
    private String jenkinsfileText;

}
