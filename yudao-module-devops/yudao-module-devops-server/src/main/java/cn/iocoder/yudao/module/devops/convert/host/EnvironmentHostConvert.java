package cn.iocoder.yudao.module.devops.convert.host;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import cn.iocoder.yudao.module.devops.enums.HostAuthTypeEnum;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface EnvironmentHostConvert {

    EnvironmentHostConvert INSTANCE = Mappers.getMapper(EnvironmentHostConvert.class);

    EnvironmentHostDO convert(EnvironmentHostSaveReqVO bean);

    @Mapping(target = "credentialConfigured", ignore = true)
    EnvironmentHostRespVO convert(EnvironmentHostDO bean);

    List<EnvironmentHostRespVO> convertList(List<EnvironmentHostDO> list);

    PageResult<EnvironmentHostRespVO> convertPage(PageResult<EnvironmentHostDO> page);

    @AfterMapping
    default void fillCredentialConfigured(EnvironmentHostDO bean, @MappingTarget EnvironmentHostRespVO respVO) {
        if (HostAuthTypeEnum.PASSWORD.getAuthType().equals(bean.getAuthType())) {
            respVO.setCredentialConfigured(StrUtil.isNotBlank(bean.getPassword()));
            return;
        }
        if (HostAuthTypeEnum.PRIVATE_KEY.getAuthType().equals(bean.getAuthType())) {
            respVO.setCredentialConfigured(StrUtil.isNotBlank(bean.getPrivateKey()));
            return;
        }
        respVO.setCredentialConfigured(false);
    }

}
