package cn.iocoder.yudao.module.devops.framework.kubernetes;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentConnectionCheckRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesConfigReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesNamespaceRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.framework.infra.EnvironmentConnector;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBECONFIG_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBERNETES_CONNECTION_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBERNETES_NAMESPACE_REQUIRED;

/**
 * Kubernetes 环境连接器。
 */
@Component
public class KubernetesEnvironmentConnector implements EnvironmentConnector {

    @Resource
    private KubernetesClientFactory kubernetesClientFactory;

    @Override
    public String getInfraType() {
        return EnvironmentInfraTypeEnum.K8S.getInfraType();
    }

    @Override
    public String buildInfraConfig(EnvironmentSaveReqVO reqVO, EnvironmentDO oldEnvironment) {
        EnvironmentKubernetesConfigReqVO kubernetesConfig = reqVO.getKubernetesConfig();
        KubernetesEnvironmentConfig oldConfig = parseOldConfig(oldEnvironment);
        String kubeconfig = kubernetesConfig == null ? null : kubernetesConfig.getKubeconfig();
        if (StrUtil.isBlank(kubeconfig) && oldConfig != null) {
            kubeconfig = oldConfig.getKubeconfig();
        }
        if (StrUtil.isBlank(kubeconfig)) {
            throw exception(ENVIRONMENT_KUBECONFIG_REQUIRED);
        }

        String namespace = kubernetesConfig == null ? null : kubernetesConfig.getNamespace();
        if (StrUtil.isBlank(namespace) && oldConfig != null) {
            namespace = oldConfig.getNamespace();
        }
        if (StrUtil.isBlank(namespace)) {
            throw exception(ENVIRONMENT_KUBERNETES_NAMESPACE_REQUIRED);
        }

        KubernetesEnvironmentConfig config = new KubernetesEnvironmentConfig();
        config.setKubeconfig(kubeconfig);
        config.setNamespace(namespace);
        if (kubernetesConfig != null && StrUtil.isNotBlank(kubernetesConfig.getKubeconfig())) {
            validateConfig(config);
        }
        return JsonUtils.toJsonString(config);
    }

    @Override
    public EnvironmentConnectionCheckRespVO checkConnection(EnvironmentDO environment) {
        try (KubernetesClient client = createClient(environment)) {
            int namespaceCount = client.namespaces().list().getItems().size();
            EnvironmentConnectionCheckRespVO respVO = new EnvironmentConnectionCheckRespVO();
            respVO.setInfraType(getInfraType());
            respVO.setNamespaceCount(namespaceCount);
            respVO.setMessage(StrUtil.format("连接成功，Namespace 数量：{}", namespaceCount));
            return respVO;
        } catch (KubernetesClientException ex) {
            throw exception(ENVIRONMENT_KUBERNETES_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    public List<EnvironmentKubernetesNamespaceRespVO> listNamespaces(EnvironmentDO environment) {
        try (KubernetesClient client = createClient(environment)) {
            return client.namespaces().list().getItems().stream()
                    .map(this::convertNamespace)
                    .toList();
        } catch (KubernetesClientException ex) {
            throw exception(ENVIRONMENT_KUBERNETES_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    private void validateConfig(KubernetesEnvironmentConfig config) {
        try (KubernetesClient ignored = kubernetesClientFactory.create(config.getKubeconfig())) {
            // 构造客户端即可完成 kubeconfig 基础解析校验，避免保存明显无效的配置。
        }
    }

    private KubernetesEnvironmentConfig parseOldConfig(EnvironmentDO oldEnvironment) {
        if (oldEnvironment == null || !getInfraType().equals(oldEnvironment.getInfraType())
                || StrUtil.isBlank(oldEnvironment.getInfraConfig())) {
            return null;
        }
        return JsonUtils.parseObject(oldEnvironment.getInfraConfig(), KubernetesEnvironmentConfig.class);
    }

    private KubernetesClient createClient(EnvironmentDO environment) {
        KubernetesEnvironmentConfig config = JsonUtils.parseObject(environment.getInfraConfig(), KubernetesEnvironmentConfig.class);
        if (config == null || StrUtil.isBlank(config.getKubeconfig())) {
            throw exception(ENVIRONMENT_KUBECONFIG_REQUIRED);
        }
        return kubernetesClientFactory.create(config.getKubeconfig());
    }

    private EnvironmentKubernetesNamespaceRespVO convertNamespace(Namespace namespace) {
        EnvironmentKubernetesNamespaceRespVO respVO = new EnvironmentKubernetesNamespaceRespVO();
        respVO.setName(namespace.getMetadata().getName());
        respVO.setCreationTimestamp(namespace.getMetadata().getCreationTimestamp());
        respVO.setStatus(namespace.getStatus() == null ? null : namespace.getStatus().getPhase());
        return respVO;
    }

}
