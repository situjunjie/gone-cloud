package cn.iocoder.yudao.module.devops.dal.mysql.pipeline;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.enums.PipelineDefinitionVersionStatusEnum;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PipelineDefinitionVersionMapper extends BaseMapperX<PipelineDefinitionVersionDO> {

    default PipelineDefinitionVersionDO selectDraftByDefinitionId(Long definitionId) {
        return selectOne(PipelineDefinitionVersionDO::getDefinitionId, definitionId,
                PipelineDefinitionVersionDO::getVersionStatus, PipelineDefinitionVersionStatusEnum.DRAFT.getStatus());
    }

    default List<PipelineDefinitionVersionDO> selectListByDefinitionId(Long definitionId) {
        return selectList(PipelineDefinitionVersionDO::getDefinitionId, definitionId);
    }

    default List<PipelineDefinitionVersionDO> selectPublishedListByDefinitionId(Long definitionId) {
        return selectList(PipelineDefinitionVersionDO::getDefinitionId, definitionId,
                PipelineDefinitionVersionDO::getVersionStatus, PipelineDefinitionVersionStatusEnum.PUBLISHED.getStatus());
    }

}
