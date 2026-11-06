package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineSshProperties;
import cn.iocoder.yudao.module.devops.service.host.EnvironmentHostService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_SSH_TARGET_INVALID;

/** Resolve a host only inside the current run's tenant, application and HOST environment. */
@Component
public class PipelineSshTargetResolver {

    @Resource
    private EnvironmentHostService environmentHostService;
    @Resource
    private ApplicationEnvMapper applicationEnvMapper;
    @Resource
    private PipelineSshProperties properties;

    public EnvironmentHostDO resolveHost(PipelineRunDO run, long hostId) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || !tenantId.equals(run.getTenantId()) || TenantContextHolder.isIgnore()) {
            throw exception(PIPELINE_SSH_TARGET_INVALID, "租户上下文无效");
        }
        if (run.getApplicationEnvId() == null || run.getAppId() == null) {
            throw exception(PIPELINE_SSH_TARGET_INVALID, "SSH Command 需要关联应用环境");
        }
        ApplicationEnvDO appEnv = applicationEnvMapper.selectById(run.getApplicationEnvId());
        EnvironmentHostDO host = environmentHostService.validateHostExists(hostId);
        environmentHostService.validateHostEnvironment(appEnv.getEnvId());
        return host;
    }

    public String resolveWorkspace(long hostId, Path workspace) {
        PipelineSshProperties.WorkspaceMapping mapping = properties.getHosts().get(hostId);
        if (mapping == null || workspace == null || mapping.getLocalRoot() == null || mapping.getRemoteRoot() == null) {
            throw exception(PIPELINE_SSH_TARGET_INVALID, "缺少 SSH 主机共享工作区映射");
        }
        Path localRoot = Path.of(mapping.getLocalRoot()).normalize();
        Path local = workspace.toAbsolutePath().normalize();
        String remoteRoot = mapping.getRemoteRoot();
        if (!localRoot.isAbsolute() || localRoot.getNameCount() == 0 || !local.startsWith(localRoot) || !remoteRoot.startsWith("/")
                || remoteRoot.indexOf('\0') >= 0 || Path.of(remoteRoot).normalize().toString().equals("/")) {
            throw exception(PIPELINE_SSH_TARGET_INVALID, "SSH 工作区映射必须是非根目录绝对路径且包含当前 source workspace");
        }
        return Path.of(remoteRoot).resolve(localRoot.relativize(local)).normalize().toString();
    }
}
