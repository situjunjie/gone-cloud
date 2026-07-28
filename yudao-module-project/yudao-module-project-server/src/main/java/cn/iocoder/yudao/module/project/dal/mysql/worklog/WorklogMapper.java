package cn.iocoder.yudao.module.project.dal.mysql.worklog;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogFilterReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogPageReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.worklog.SprintWorklogSummaryResult;
import cn.iocoder.yudao.module.project.dal.dataobject.worklog.WorklogDO;
import cn.iocoder.yudao.module.project.dal.dataobject.worklog.WorklogSummaryResult;
import cn.iocoder.yudao.module.project.enums.WorklogGroupByEnum;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface WorklogMapper extends BaseMapperX<WorklogDO> {

    default LambdaQueryWrapperX<WorklogDO> buildQueryWrapper(WorklogFilterReqVO reqVO) {
        return buildQueryWrapper(reqVO, null);
    }

    default LambdaQueryWrapperX<WorklogDO> buildQueryWrapper(WorklogFilterReqVO reqVO,
                                                               Collection<Long> scopedWorkItemIds) {
        LambdaQueryWrapperX<WorklogDO> query = new LambdaQueryWrapperX<WorklogDO>()
                .eq(WorklogDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(WorklogDO::getType, reqVO.getType())
                .eqIfPresent(WorklogDO::getCategory, reqVO.getCategory())
                .eqIfPresent(WorklogDO::getOwnerUserId, reqVO.getOwnerUserId())
                .geIfPresent(WorklogDO::getWorkDate, reqVO.getWorkDateStart())
                .leIfPresent(WorklogDO::getWorkDate, reqVO.getWorkDateEnd());
        if (scopedWorkItemIds == null) {
            query.eqIfPresent(WorklogDO::getWorkItemId, reqVO.getWorkItemId());
        } else {
            query.in(WorklogDO::getWorkItemId, scopedWorkItemIds);
        }
        if (reqVO.getSprintId() != null) {
            query.apply("EXISTS (SELECT 1 FROM proj_work_item wi WHERE wi.id = proj_worklog.work_item_id "
                    + "AND wi.project_id = proj_worklog.project_id AND wi.tenant_id = proj_worklog.tenant_id "
                    + "AND wi.deleted = 0 AND wi.sprint_id = {0})", reqVO.getSprintId());
        }
        return query;
    }

    default PageResult<WorklogDO> selectPage(WorklogPageReqVO reqVO) {
        return selectPage(reqVO, buildQueryWrapper(reqVO));
    }

    default PageResult<WorklogDO> selectPage(WorklogPageReqVO reqVO, LambdaQueryWrapperX<WorklogDO> query) {
        return selectPage(reqVO.toPageParam(), query.orderByDesc(WorklogDO::getWorkDate)
                .orderByDesc(WorklogDO::getId));
    }

    default WorklogDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<WorklogDO>().eq(WorklogDO::getId, id).last("FOR UPDATE"));
    }

    WorklogSummaryResult selectSummary(@Param(Constants.WRAPPER) LambdaQueryWrapperX<WorklogDO> queryWrapper);

    List<WorklogSummaryResult> selectGroupSummary(
            @Param(Constants.WRAPPER) LambdaQueryWrapperX<WorklogDO> queryWrapper,
            @Param("groupBy") WorklogGroupByEnum groupBy);

    List<SprintWorklogSummaryResult> selectSprintActualHours(@Param("projectId") Long projectId);

    default boolean existsByWorkItemId(Long workItemId) {
        return selectCount(WorklogDO::getWorkItemId, workItemId) > 0;
    }

    default void deleteByWorkItemId(Long workItemId) {
        delete(WorklogDO::getWorkItemId, workItemId);
    }

}
