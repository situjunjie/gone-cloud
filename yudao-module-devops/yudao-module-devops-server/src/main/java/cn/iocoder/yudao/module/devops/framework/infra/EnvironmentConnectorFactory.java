package cn.iocoder.yudao.module.devops.framework.infra;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED;

/**
 * 环境基础设施连接器工厂。
 */
@Component
public class EnvironmentConnectorFactory {

    private final Map<String, EnvironmentConnector> connectors;

    public EnvironmentConnectorFactory(List<EnvironmentConnector> connectors) {
        this.connectors = connectors.stream()
                .collect(Collectors.toMap(EnvironmentConnector::getInfraType, Function.identity()));
    }

    public EnvironmentConnector getConnector(String infraType) {
        EnvironmentConnector connector = connectors.get(infraType);
        if (connector == null) {
            throw exception(ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED);
        }
        return connector;
    }

}
