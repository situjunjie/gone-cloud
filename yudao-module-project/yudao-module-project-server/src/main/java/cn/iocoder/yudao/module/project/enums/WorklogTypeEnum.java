package cn.iocoder.yudao.module.project.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum WorklogTypeEnum implements ArrayValuable<String> {

    ESTIMATED("ESTIMATED", "预估工时"),
    ACTUAL("ACTUAL", "实际工时");

    public static final String[] ARRAYS = Arrays.stream(values()).map(WorklogTypeEnum::getValue)
            .toArray(String[]::new);

    private final String value;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

    public static WorklogTypeEnum of(String value) {
        return Arrays.stream(values()).filter(item -> item.value.equals(value)).findFirst().orElse(null);
    }

}
