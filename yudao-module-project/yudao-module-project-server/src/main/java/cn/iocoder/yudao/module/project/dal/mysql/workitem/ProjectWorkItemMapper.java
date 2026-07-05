package cn.iocoder.yudao.module.project.dal.mysql.workitem;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemQueryOperatorEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

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
        applyConditions(query, reqVO.getConditions());
        query.orderByDesc(ProjectWorkItemDO::getId);
        return selectPage(reqVO, query);
    }

    private void applyConditions(LambdaQueryWrapperX<ProjectWorkItemDO> query,
                                 List<ProjectWorkItemPageReqVO.Condition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return;
        }
        for (ProjectWorkItemPageReqVO.Condition condition : conditions) {
            applyCondition(query, condition);
        }
    }

    private void applyCondition(LambdaQueryWrapperX<ProjectWorkItemDO> query,
                                ProjectWorkItemPageReqVO.Condition condition) {
        SFunction<ProjectWorkItemDO, ?> column = condition.getFieldEnum().getColumn();
        ProjectWorkItemQueryOperatorEnum operator = condition.getOperatorEnum();
        switch (operator) {
            case EQ -> query.eq(column, condition.getParsedValue());
            case NE -> query.ne(column, condition.getParsedValue());
            case GT -> query.gt(column, condition.getParsedValue());
            case GE -> query.ge(column, condition.getParsedValue());
            case LT -> query.lt(column, condition.getParsedValue());
            case LE -> query.le(column, condition.getParsedValue());
            case BETWEEN -> query.between(column, condition.getParsedValue(), condition.getParsedValueEnd());
            case IN -> query.in(column, condition.getParsedValues());
            case NOT_IN -> query.notIn(column, condition.getParsedValues());
            case CONTAINS -> applyContainsCondition(query, condition, column);
            case NOT_CONTAINS -> applyNotContainsCondition(query, condition, column);
        }
    }

    private void applyContainsCondition(LambdaQueryWrapperX<ProjectWorkItemDO> query,
                                        ProjectWorkItemPageReqVO.Condition condition,
                                        SFunction<ProjectWorkItemDO, ?> column) {
        if (condition.getFieldEnum().isText()) {
            query.like(column, condition.getParsedValue());
            return;
        }
        query.in(column, condition.getParsedValues());
    }

    private void applyNotContainsCondition(LambdaQueryWrapperX<ProjectWorkItemDO> query,
                                           ProjectWorkItemPageReqVO.Condition condition,
                                           SFunction<ProjectWorkItemDO, ?> column) {
        if (condition.getFieldEnum().isText()) {
            query.notLike(column, condition.getParsedValue());
            return;
        }
        query.notIn(column, condition.getParsedValues());
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

    default int updateFieldById(Long id, SFunction<ProjectWorkItemDO, ?> column, Object value, LocalDateTime updateTime) {
        return update(null, new LambdaUpdateWrapper<ProjectWorkItemDO>()
                .eq(ProjectWorkItemDO::getId, id)
                .set(column, value)
                .set(ProjectWorkItemDO::getUpdateTime, updateTime));
    }

}
