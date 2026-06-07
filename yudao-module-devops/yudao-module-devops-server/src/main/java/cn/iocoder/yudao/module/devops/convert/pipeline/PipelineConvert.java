package cn.iocoder.yudao.module.devops.convert.pipeline;

import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineDefinitionRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineDefinitionVersionRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface PipelineConvert {

    PipelineConvert INSTANCE = Mappers.getMapper(PipelineConvert.class);

    PipelineDefinitionRespVO convert(PipelineDefinitionDO bean);

    PipelineDefinitionVersionRespVO convert(PipelineDefinitionVersionDO bean);

    List<PipelineDefinitionVersionRespVO> convertVersionList(List<PipelineDefinitionVersionDO> list);

}
