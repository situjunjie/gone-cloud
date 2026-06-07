package cn.iocoder.yudao.module.devops.dal.mysql.pipeline;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PipelineDefinitionMapper extends BaseMapperX<PipelineDefinitionDO> {

    default PipelineDefinitionDO selectByApplicationEnvId(Long applicationEnvId) {
        return selectOne(PipelineDefinitionDO::getApplicationEnvId, applicationEnvId);
    }

    default List<PipelineDefinitionDO> selectListByApplicationEnvIds(Collection<Long> applicationEnvIds) {
        return selectList(PipelineDefinitionDO::getApplicationEnvId, applicationEnvIds);
    }

    default PipelineDefinitionDO selectByDefinitionKey(String definitionKey) {
        return selectOne(PipelineDefinitionDO::getDefinitionKey, definitionKey);
    }

}
