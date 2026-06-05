package cn.iocoder.yudao.module.devops.convert.application;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationEnvRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationEnvSaveReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ApplicationConvert {

    ApplicationConvert INSTANCE = Mappers.getMapper(ApplicationConvert.class);

    ApplicationDO convert(ApplicationSaveReqVO bean);

    ApplicationRespVO convert(ApplicationDO bean);

    PageResult<ApplicationRespVO> convertPage(PageResult<ApplicationDO> page);

    ApplicationEnvDO convert(ApplicationEnvSaveReqVO bean);

    ApplicationEnvRespVO convert(ApplicationEnvDO bean);

    List<ApplicationEnvRespVO> convertEnvList(List<ApplicationEnvDO> list);

}
