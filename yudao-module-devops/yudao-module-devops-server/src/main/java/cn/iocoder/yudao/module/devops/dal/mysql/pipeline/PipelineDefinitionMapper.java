package cn.iocoder.yudao.module.devops.dal.mysql.pipeline;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PipelineDefinitionMapper extends BaseMapperX<PipelineDefinitionDO> {

    default PipelineDefinitionDO selectByApplicationEnvId(Long applicationEnvId) {
        return selectOne(PipelineDefinitionDO::getApplicationEnvId, applicationEnvId);
    }

    default PipelineDefinitionDO selectByDefinitionKey(String definitionKey) {
        return selectOne(PipelineDefinitionDO::getDefinitionKey, definitionKey);
    }

}
