package cn.iocoder.yudao.module.devops.convert.variablegroup;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.variablegroup.vo.VariableGroupRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.variablegroup.vo.VariableGroupSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.variablegroup.VariableGroupDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface VariableGroupConvert {
    VariableGroupConvert INSTANCE = Mappers.getMapper(VariableGroupConvert.class);
    VariableGroupDO convert(VariableGroupSaveReqVO bean);
    VariableGroupRespVO convert(VariableGroupDO bean);
    PageResult<VariableGroupRespVO> convertPage(PageResult<VariableGroupDO> page);
}
