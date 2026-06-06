package cn.iocoder.yudao.module.devops.framework.infra;

import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentConnectionCheckRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;

/**
 * 环境基础设施连接器。
 */
public interface EnvironmentConnector {

    /**
     * @return 支持的基础设施类型
     */
    String getInfraType();

    /**
     * 构建并校验基础设施连接配置。
     *
     * @param reqVO 请求
     * @param oldEnvironment 旧环境，创建时为空
     * @return 要保存到环境表的配置 JSON
     */
    String buildInfraConfig(EnvironmentSaveReqVO reqVO, EnvironmentDO oldEnvironment);

    /**
     * 检测环境连接。
     *
     * @param environment 环境
     * @return 检测结果
     */
    EnvironmentConnectionCheckRespVO checkConnection(EnvironmentDO environment);

}
