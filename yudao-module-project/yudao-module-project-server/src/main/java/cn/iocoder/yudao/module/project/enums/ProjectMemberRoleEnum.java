package cn.iocoder.yudao.module.project.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum ProjectMemberRoleEnum implements ArrayValuable<String> {

    OWNER("OWNER", "负责人"),
    ADMIN("ADMIN", "管理员"),
    MEMBER("MEMBER", "成员"),
    VIEWER("VIEWER", "只读");

    public static final String[] ARRAYS = Arrays.stream(values()).map(ProjectMemberRoleEnum::getValue)
            .toArray(String[]::new);

    private final String value;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
