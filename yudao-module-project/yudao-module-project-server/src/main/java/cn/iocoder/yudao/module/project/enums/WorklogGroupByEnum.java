package cn.iocoder.yudao.module.project.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum WorklogGroupByEnum implements ArrayValuable<String> {

    WORK_ITEM("WORK_ITEM"),
    OWNER("OWNER"),
    CATEGORY("CATEGORY"),
    SPRINT("SPRINT");

    public static final String[] ARRAYS = Arrays.stream(values()).map(WorklogGroupByEnum::getValue)
            .toArray(String[]::new);

    private final String value;

    @Override
    public String[] array() {
        return ARRAYS;
    }

    public static WorklogGroupByEnum of(String value) {
        return Arrays.stream(values()).filter(item -> item.value.equals(value)).findFirst().orElse(null);
    }

}
