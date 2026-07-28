package cn.iocoder.yudao.module.project.controller.admin.worklog.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.project.enums.WorklogGroupByEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorklogSummaryReqVO extends WorklogFilterReqVO {

    @InEnum(value = WorklogGroupByEnum.class, message = "汇总分组维度不正确")
    private String groupBy;

}
