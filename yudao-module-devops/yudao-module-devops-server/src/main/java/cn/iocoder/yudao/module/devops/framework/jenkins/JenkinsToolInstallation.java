package cn.iocoder.yudao.module.devops.framework.jenkins;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Jenkins 全局工具配置。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JenkinsToolInstallation {

    /**
     * 工具类型：JDK、MAVEN。
     */
    private String type;
    /**
     * Jenkins 全局工具名称。
     */
    private String name;
    /**
     * 工具安装目录。自动安装的工具可能为空。
     */
    private String home;

}
