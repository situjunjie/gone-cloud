package cn.iocoder.yudao.module.project.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum ProjectStatusEnum implements ArrayValuable<String> {

    ACTIVE("ACTIVE", "正常"),
    ARCHIVED("ARCHIVED", "已归档");

    public static final String[] ARRAYS = Arrays.stream(values()).map(ProjectStatusEnum::getValue)
            .toArray(String[]::new);

    private final String value;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
