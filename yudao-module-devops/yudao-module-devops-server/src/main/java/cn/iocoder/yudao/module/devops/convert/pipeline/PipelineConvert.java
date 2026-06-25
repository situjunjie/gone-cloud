package cn.iocoder.yudao.module.devops.convert.pipeline;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineDefinitionRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineDefinitionVersionRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(imports = JsonUtils.class)
public interface PipelineConvert {

    PipelineConvert INSTANCE = Mappers.getMapper(PipelineConvert.class);

    PipelineDefinitionRespVO convert(PipelineDefinitionDO bean);

    PipelineDefinitionVersionRespVO convert(PipelineDefinitionVersionDO bean);

    List<PipelineDefinitionVersionRespVO> convertVersionList(List<PipelineDefinitionVersionDO> list);

    @Mapping(target = "context", expression = "java(JsonUtils.parseMap(bean.getContextJson()))")
    @Mapping(target = "result", expression = "java(JsonUtils.parseMap(bean.getResultJson()))")
    PipelineRunLogRespVO convert(PipelineRunLogDO bean);

}
