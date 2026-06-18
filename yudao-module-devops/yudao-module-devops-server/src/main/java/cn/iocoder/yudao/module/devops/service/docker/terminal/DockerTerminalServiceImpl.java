package cn.iocoder.yudao.module.devops.service.docker.terminal;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.mysql.environment.EnvironmentMapper;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.framework.docker.DockerClientFactory;
import cn.iocoder.yudao.module.devops.framework.docker.DockerEnvironmentConfig;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DOCKER_CONTAINER_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DOCKER_CONTAINER_NOT_RUNNING;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DOCKER_TERMINAL_EXEC_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_DOCKER_CONNECTION_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_DOCKER_HOST_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_NOT_EXISTS;

/**
 * Docker 容器终端服务实现。
 */
@Service
@Validated
public class DockerTerminalServiceImpl implements DockerTerminalService {

    private static final String CONTAINER_STATE_RUNNING = "running";
    private static final String INTERACTIVE_SHELL_COMMAND = "if command -v bash >/dev/null 2>&1; then exec bash -il; "
            + "elif command -v ash >/dev/null 2>&1; then exec ash -i; else exec sh -i; fi";

    @Resource
    private EnvironmentMapper environmentMapper;
    @Resource
    private DockerClientFactory dockerClientFactory;

    @Override
    public DockerTerminalSession openTerminal(Long environmentId, String containerId) {
        EnvironmentDO environment = validateEnvironment(environmentId);
        DockerClient client = createClient(environment);
        try {
            InspectContainerResponse container = inspectContainer(client, containerId);
            validateRunning(container);
            PipedInputStream stdin = new PipedInputStream();
            PipedOutputStream input = new PipedOutputStream(stdin);
            ExecCreateCmdResponse exec = client.execCreateCmd(container.getId())
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withTty(true)
                    .withCmd("/bin/sh", "-c", INTERACTIVE_SHELL_COMMAND)
                    .exec();
            return new DockerTerminalSession(client, exec.getId(), stdin, input);
        } catch (NotFoundException ex) {
            closeQuietly(client);
            throw exception(DOCKER_CONTAINER_NOT_EXISTS, containerId);
        } catch (IOException ex) {
            closeQuietly(client);
            throw exception(DOCKER_TERMINAL_EXEC_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        } catch (DockerException ex) {
            closeQuietly(client);
            throw exception(DOCKER_TERMINAL_EXEC_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        } catch (RuntimeException ex) {
            closeQuietly(client);
            throw ex;
        }
    }

    private EnvironmentDO validateEnvironment(Long environmentId) {
        EnvironmentDO environment = environmentMapper.selectById(environmentId);
        if (environment == null) {
            throw exception(ENVIRONMENT_NOT_EXISTS);
        }
        if (!EnvironmentInfraTypeEnum.DOCKER.getInfraType().equals(environment.getInfraType())) {
            throw exception(ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED);
        }
        return environment;
    }

    private DockerClient createClient(EnvironmentDO environment) {
        DockerEnvironmentConfig config = JsonUtils.parseObject(environment.getInfraConfig(), DockerEnvironmentConfig.class);
        if (config == null || StrUtil.isBlank(config.getHost())) {
            throw exception(ENVIRONMENT_DOCKER_HOST_REQUIRED);
        }
        try {
            return dockerClientFactory.createClient(config);
        } catch (RuntimeException ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    private InspectContainerResponse inspectContainer(DockerClient client, String containerId) {
        return client.inspectContainerCmd(containerId).exec();
    }

    private void validateRunning(InspectContainerResponse container) {
        String status = container.getState() == null ? null : container.getState().getStatus();
        if (!CONTAINER_STATE_RUNNING.equals(status)) {
            throw exception(DOCKER_CONTAINER_NOT_RUNNING, StrUtil.blankToDefault(status, "unknown"));
        }
    }

    private void closeQuietly(DockerClient client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (IOException ignored) {
            // ignore
        }
    }

}
