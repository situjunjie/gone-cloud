package cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogLineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PipelineRunLogLineMapper extends BaseMapperX<PipelineRunLogLineDO> {

    default List<PipelineRunLogLineDO> selectListByCursor(Long pipelineRunId, String stepId, Long afterId,
                                                          Integer limit) {
        return selectList(new LambdaQueryWrapperX<PipelineRunLogLineDO>()
                .eq(PipelineRunLogLineDO::getPipelineRunId, pipelineRunId)
                .eqIfPresent(PipelineRunLogLineDO::getStepId, stepId)
                .gtIfPresent(PipelineRunLogLineDO::getId, afterId)
                .orderByAsc(PipelineRunLogLineDO::getId)
                .last("LIMIT " + limit));
    }

    default Long selectMaxLineNoByRunLogId(Long runLogId) {
        PipelineRunLogLineDO line = selectOne(new LambdaQueryWrapperX<PipelineRunLogLineDO>()
                .eq(PipelineRunLogLineDO::getRunLogId, runLogId)
                .orderByDesc(PipelineRunLogLineDO::getLineNo)
                .last("LIMIT 1"));
        return line == null ? 0L : line.getLineNo();
    }

}
