package cn.iocoder.yudao.module.project.controller.admin.worklog.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
public class WorklogSummaryRespVO {

    private BigDecimal estimatedHours;
    private BigDecimal actualHours;
    private String groupBy;
    private List<Group> groups;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Group {
        private String value;
        private String displayName;
        private BigDecimal estimatedHours;
        private BigDecimal actualHours;
    }

}
