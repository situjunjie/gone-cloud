package cn.iocoder.yudao.module.devops.framework.docker;

import com.github.dockerjava.api.DockerClient;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * Docker 客户端工厂。
 *
 * <p>当前提供默认 Docker daemon 客户端。后续如果需要按构建主机、租户或 Registry 凭据创建临时客户端，
 * 可以在这里扩展，不要把 docker-java 初始化细节散落到业务服务里。
 */
@Component
public class DockerClientFactory {

    @Resource
    private DockerClient dockerClient;

    /**
     * 获取默认 Docker 客户端。
     *
     * @return Docker 客户端
     */
    public DockerClient getDefaultClient() {
        return dockerClient;
    }

}
