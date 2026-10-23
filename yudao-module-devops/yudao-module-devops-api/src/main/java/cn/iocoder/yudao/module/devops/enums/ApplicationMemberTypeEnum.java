package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 应用成员类型。
 */
@Getter
@AllArgsConstructor
public enum ApplicationMemberTypeEnum {

    OWNER("OWNER"),
    PARTICIPANT("PARTICIPANT");

    private final String type;

}
