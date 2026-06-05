package cn.iocoder.yudao.module.devops.convert.environment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface EnvironmentConvert {

    EnvironmentConvert INSTANCE = Mappers.getMapper(EnvironmentConvert.class);

    EnvironmentDO convert(EnvironmentSaveReqVO bean);

    EnvironmentRespVO convert(EnvironmentDO bean);

    PageResult<EnvironmentRespVO> convertPage(PageResult<EnvironmentDO> page);

}
