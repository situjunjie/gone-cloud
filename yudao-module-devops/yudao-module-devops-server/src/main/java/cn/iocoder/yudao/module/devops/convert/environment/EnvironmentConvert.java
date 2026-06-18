package cn.iocoder.yudao.module.devops.convert.environment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.framework.docker.DockerEnvironmentConfig;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesEnvironmentConfig;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

@Mapper
public interface EnvironmentConvert {

    EnvironmentConvert INSTANCE = Mappers.getMapper(EnvironmentConvert.class);

    @Mapping(target = "infraConfig", ignore = true)
    EnvironmentDO convert(EnvironmentSaveReqVO bean);

    @Mapping(target = "infraConfigConfigured", ignore = true)
    @Mapping(target = "kubernetesNamespace", ignore = true)
    @Mapping(target = "dockerHost", ignore = true)
    @Mapping(target = "dockerTlsEnabled", ignore = true)
    EnvironmentRespVO convert(EnvironmentDO bean);

    PageResult<EnvironmentRespVO> convertPage(PageResult<EnvironmentDO> page);

    @AfterMapping
    default void fillInfraConfigSummary(EnvironmentDO bean, @MappingTarget EnvironmentRespVO respVO) {
        respVO.setInfraConfigConfigured(StrUtil.isNotBlank(bean.getInfraConfig()));
        if (StrUtil.isBlank(bean.getInfraConfig())) {
            return;
        }
        if (EnvironmentInfraTypeEnum.K8S.getInfraType().equals(bean.getInfraType())) {
            KubernetesEnvironmentConfig config = JsonUtils.parseObject(bean.getInfraConfig(), KubernetesEnvironmentConfig.class);
            respVO.setKubernetesNamespace(config == null ? null : config.getNamespace());
            return;
        }
        if (EnvironmentInfraTypeEnum.DOCKER.getInfraType().equals(bean.getInfraType())) {
            DockerEnvironmentConfig config = JsonUtils.parseObject(bean.getInfraConfig(), DockerEnvironmentConfig.class);
            respVO.setDockerHost(config == null ? null : config.getHost());
            respVO.setDockerTlsEnabled(config == null ? null : config.getTlsVerify());
        }
    }

}
