package cn.iocoder.yudao.module.project.dal.dataobject.workitem;

import lombok.Data;

/**
 * 迭代工作项数量聚合查询结果。
 */
@Data
public class ProjectSprintWorkItemCountResult {

    private Long sprintId;
    private Long totalCount;
    private Long completedCount;

}
