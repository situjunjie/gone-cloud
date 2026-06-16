package cn.iocoder.yudao.module.devops.dal.mysql.pipeline.job;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.job.PipelineRunJobDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 流水线运行任务 Mapper。
 */
@Mapper
public interface PipelineRunJobMapper extends BaseMapperX<PipelineRunJobDO> {

    /**
     * 查询指定流水线运行下的任务列表。
     *
     * @param pipelineRunId 流水线运行编号
     * @return 任务列表
     */
    default List<PipelineRunJobDO> selectListByPipelineRunId(Long pipelineRunId) {
        return selectList(new LambdaQueryWrapperX<PipelineRunJobDO>()
                .eq(PipelineRunJobDO::getPipelineRunId, pipelineRunId)
                .orderByAsc(PipelineRunJobDO::getSort)
                .orderByAsc(PipelineRunJobDO::getId));
    }

    /**
     * 查询指定流水线运行下的单个任务。
     *
     * @param pipelineRunId 流水线运行编号
     * @param jobId 任务编号
     * @return 任务记录
     */
    default PipelineRunJobDO selectByPipelineRunIdAndJobId(Long pipelineRunId, String jobId) {
        return selectOne(new LambdaQueryWrapperX<PipelineRunJobDO>()
                .eq(PipelineRunJobDO::getPipelineRunId, pipelineRunId)
                .eq(PipelineRunJobDO::getJobId, jobId)
                .last("LIMIT 1"));
    }

    /**
     * 查询指定流水线运行下处于指定状态集合的任务列表。
     *
     * @param pipelineRunId 流水线运行编号
     * @param statuses 任务状态集合
     * @return 任务列表
     */
    default List<PipelineRunJobDO> selectListByPipelineRunIdAndStatuses(Long pipelineRunId,
                                                                        Collection<String> statuses) {
        return selectList(new LambdaQueryWrapperX<PipelineRunJobDO>()
                .eq(PipelineRunJobDO::getPipelineRunId, pipelineRunId)
                .in(PipelineRunJobDO::getStatus, statuses)
                .orderByAsc(PipelineRunJobDO::getSort)
                .orderByAsc(PipelineRunJobDO::getId));
    }

}
