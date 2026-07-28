package cn.iocoder.yudao.module.project.dal.dataobject.worklog;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SprintWorklogSummaryResult {

    private Long sprintId;
    private BigDecimal actualHours;

}
