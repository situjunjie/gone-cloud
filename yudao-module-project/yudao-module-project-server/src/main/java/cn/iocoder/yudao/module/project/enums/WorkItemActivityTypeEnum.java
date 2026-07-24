package cn.iocoder.yudao.module.project.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WorkItemActivityTypeEnum {

    COMMENT("COMMENT", "评论"),
    OPERATION("OPERATION", "操作记录");

    private final String value;
    private final String name;

}
