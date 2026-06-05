package cn.iocoder.yudao.module.devops.convert.change;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeEnvRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeEnvDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ChangeConvert {

    ChangeConvert INSTANCE = Mappers.getMapper(ChangeConvert.class);

    ChangeDO convert(ChangeSaveReqVO bean);

    ChangeRespVO convert(ChangeDO bean);

    PageResult<ChangeRespVO> convertPage(PageResult<ChangeDO> page);

    ChangeEnvRespVO convert(ChangeEnvDO bean);

    List<ChangeEnvRespVO> convertEnvList(List<ChangeEnvDO> list);

}
