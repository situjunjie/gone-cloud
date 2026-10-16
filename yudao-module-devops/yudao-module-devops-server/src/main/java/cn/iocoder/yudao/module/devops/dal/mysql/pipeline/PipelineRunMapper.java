package cn.iocoder.yudao.module.devops.dal.mysql.pipeline;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PipelineRunMapper extends BaseMapperX<PipelineRunDO> {

    default List<PipelineRunDO> selectListByApplicationEnvIdAndStatuses(Long applicationEnvId,
                                                                        Collection<Integer> statuses) {
        return selectList(new LambdaQueryWrapperX<PipelineRunDO>()
                .eq(PipelineRunDO::getApplicationEnvId, applicationEnvId)
                .in(PipelineRunDO::getRunStatus, statuses));
    }

    default PipelineRunDO selectLatestByApplicationEnvIdAndStatuses(Long applicationEnvId,
                                                                    Collection<Integer> statuses) {
        return selectOne(new LambdaQueryWrapperX<PipelineRunDO>()
                .eq(PipelineRunDO::getApplicationEnvId, applicationEnvId)
                .in(PipelineRunDO::getRunStatus, statuses)
                .orderByDesc(PipelineRunDO::getTriggeredAt)
                .orderByDesc(PipelineRunDO::getId)
                .last("LIMIT 1"));
    }

    default PipelineRunDO selectLatestByApplicationEnvId(Long applicationEnvId) {
        return selectOne(new LambdaQueryWrapperX<PipelineRunDO>()
                .eq(PipelineRunDO::getApplicationEnvId, applicationEnvId)
                .orderByDesc(PipelineRunDO::getTriggeredAt)
                .orderByDesc(PipelineRunDO::getId)
                .last("LIMIT 1"));
    }

    default PipelineRunDO selectLatestByApplicationEnvIdAndBranchPrefix(Long applicationEnvId, String branchPrefix) {
        return selectOne(new LambdaQueryWrapperX<PipelineRunDO>()
                .eq(PipelineRunDO::getApplicationEnvId, applicationEnvId)
                .likeRightIfPresent(PipelineRunDO::getBranchName, branchPrefix)
                .orderByDesc(PipelineRunDO::getTriggeredAt)
                .orderByDesc(PipelineRunDO::getId)
                .last("LIMIT 1"));
    }

    /**
     * 查询应用环境下指定分支前缀的全部流水线运行，用于清理历史部署分支。
     *
     * <p>不做排序：清理顺序由调用方决定，且同一分支名可能被多次运行复用。
     */
    default List<PipelineRunDO> selectListByApplicationEnvIdAndBranchPrefix(Long applicationEnvId, String branchPrefix) {
        return selectList(new LambdaQueryWrapperX<PipelineRunDO>()
                .eq(PipelineRunDO::getApplicationEnvId, applicationEnvId)
                .likeRightIfPresent(PipelineRunDO::getBranchName, branchPrefix));
    }

}
