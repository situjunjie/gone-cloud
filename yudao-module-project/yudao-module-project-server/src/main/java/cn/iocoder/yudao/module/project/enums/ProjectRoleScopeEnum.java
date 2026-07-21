package cn.iocoder.yudao.module.project.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProjectRoleScopeEnum {

    GLOBAL("GLOBAL"),
    PROJECT("PROJECT");

    private final String value;

}
