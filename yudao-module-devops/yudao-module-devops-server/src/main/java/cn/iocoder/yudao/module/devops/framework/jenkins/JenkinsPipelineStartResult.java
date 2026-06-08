package cn.iocoder.yudao.module.devops.framework.jenkins;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Jenkins 流水线启动结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JenkinsPipelineStartResult {

    private Boolean skipped;
    private String queueId;

}
