package cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PipelineRunLogMapper extends BaseMapperX<PipelineRunLogDO> {

    default List<PipelineRunLogDO> selectListByPipelineRunId(Long pipelineRunId) {
        return selectList(new LambdaQueryWrapperX<PipelineRunLogDO>()
                .eq(PipelineRunLogDO::getPipelineRunId, pipelineRunId)
                .orderByAsc(PipelineRunLogDO::getSort)
                .orderByAsc(PipelineRunLogDO::getId));
    }

    default List<PipelineRunLogDO> selectListByParentId(Long parentId) {
        return selectList(new LambdaQueryWrapperX<PipelineRunLogDO>()
                .eq(PipelineRunLogDO::getParentId, parentId)
                .orderByAsc(PipelineRunLogDO::getSort)
                .orderByAsc(PipelineRunLogDO::getId));
    }

    default PipelineRunLogDO selectByPipelineRunIdAndNodeType(Long pipelineRunId, String nodeType) {
        return selectOne(new LambdaQueryWrapperX<PipelineRunLogDO>()
                .eq(PipelineRunLogDO::getPipelineRunId, pipelineRunId)
                .eq(PipelineRunLogDO::getStepType, nodeType)
                .isNull(PipelineRunLogDO::getParentId)
                .last("LIMIT 1"));
    }

    default PipelineRunLogDO selectByPipelineRunIdAndNodeId(Long pipelineRunId, String nodeId) {
        return selectOne(new LambdaQueryWrapperX<PipelineRunLogDO>()
                .eq(PipelineRunLogDO::getPipelineRunId, pipelineRunId)
                .eq(PipelineRunLogDO::getStepId, nodeId)
                .isNull(PipelineRunLogDO::getParentId)
                .last("LIMIT 1"));
    }

    default PipelineRunLogDO selectActiveByPipelineRunIdAndNodeType(Long pipelineRunId, String nodeType,
                                                                    Collection<String> statuses) {
        return selectOne(new LambdaQueryWrapperX<PipelineRunLogDO>()
                .eq(PipelineRunLogDO::getPipelineRunId, pipelineRunId)
                .eq(PipelineRunLogDO::getStepType, nodeType)
                .in(PipelineRunLogDO::getStatus, statuses)
                .isNull(PipelineRunLogDO::getParentId)
                .last("LIMIT 1"));
    }

}
