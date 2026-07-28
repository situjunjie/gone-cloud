package cn.iocoder.yudao.module.project.dal.dataobject.worklog;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkItemWorklogSummaryResult {

    private Long workItemId;
    private BigDecimal estimatedHours;
    private BigDecimal actualHours;

}
