package cn.iocoder.yudao.module.devops.framework.jenkins;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DevOps Jenkins 集成配置。
 */
@Component
@ConfigurationProperties(prefix = "devops.jenkins")
@Data
public class JenkinsProperties {

    /**
     * 是否启用 Jenkins 触发。关闭时保持代码合并 MVP 的旧行为。
     */
    private Boolean enabled = false;
    /**
     * Jenkins 服务地址，例如 http://jenkins.example.com。
     */
    private String baseUrl;
    /**
     * 参数化 Pipeline Job 名称。
     */
    private String jobName;
    /**
     * Jenkins 用户名。
     */
    private String username;
    /**
     * Jenkins API Token。
     */
    private String apiToken;
    /**
     * 平台回调地址，Jenkinsfile 中传给 shared library 使用。
     */
    private String callbackUrl;
    /**
     * Jenkins 回调平台时使用的共享令牌。
     */
    private String callbackToken;

}
