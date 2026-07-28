package cn.iocoder.yudao.module.project.dal.dataobject.worklog;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorklogSummaryResult {

    private String value;
    private BigDecimal estimatedHours;
    private BigDecimal actualHours;

}
