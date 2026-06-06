package cn.iocoder.yudao.module.devops.convert.repositoryprovider;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo.RepositoryProviderRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo.RepositoryProviderSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface RepositoryProviderConvert {

    RepositoryProviderConvert INSTANCE = Mappers.getMapper(RepositoryProviderConvert.class);

    RepositoryProviderDO convert(RepositoryProviderSaveReqVO bean);

    RepositoryProviderRespVO convert(RepositoryProviderDO bean);

    PageResult<RepositoryProviderRespVO> convertPage(PageResult<RepositoryProviderDO> page);

}
