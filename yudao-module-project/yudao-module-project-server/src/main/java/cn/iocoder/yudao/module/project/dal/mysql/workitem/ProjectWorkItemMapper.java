package cn.iocoder.yudao.module.project.dal.mysql.workitem;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface ProjectWorkItemMapper extends BaseMapperX<ProjectWorkItemDO> {

    default PageResult<ProjectWorkItemDO> selectPage(ProjectWorkItemPageReqVO reqVO) {
        LambdaQueryWrapperX<ProjectWorkItemDO> query = new LambdaQueryWrapperX<ProjectWorkItemDO>()
                .eqIfPresent(ProjectWorkItemDO::getCategory, reqVO.getCategory())
                .eqIfPresent(ProjectWorkItemDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ProjectWorkItemDO::getPriority, reqVO.getPriority())
                .eqIfPresent(ProjectWorkItemDO::getAssigneeUserId, reqVO.getAssigneeUserId())
                .eqIfPresent(ProjectWorkItemDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(ProjectWorkItemDO::getIterationId, reqVO.getIterationId())
                .eqIfPresent(ProjectWorkItemDO::getCreator, reqVO.getCreator())
                .betweenIfPresent(ProjectWorkItemDO::getPlannedEndTime, reqVO.getPlannedEndTime())
                .betweenIfPresent(ProjectWorkItemDO::getCreateTime, reqVO.getCreateTime());
        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            query.and(q -> q.like(ProjectWorkItemDO::getWorkItemNo, reqVO.getKeyword())
                    .or().like(ProjectWorkItemDO::getTitle, reqVO.getKeyword()));
        }
        query.orderByDesc(ProjectWorkItemDO::getId);
        return selectPage(reqVO, query);
    }

    default Long selectCountByParentWorkItemId(Long parentWorkItemId) {
        return selectCount(ProjectWorkItemDO::getParentWorkItemId, parentWorkItemId);
    }

    default int updateWorkItemNoById(Long id, String workItemNo, LocalDateTime updateTime) {
        return update(null, new LambdaUpdateWrapper<ProjectWorkItemDO>()
                .eq(ProjectWorkItemDO::getId, id)
                .set(ProjectWorkItemDO::getWorkItemNo, workItemNo)
                .set(ProjectWorkItemDO::getUpdateTime, updateTime));
    }

}
