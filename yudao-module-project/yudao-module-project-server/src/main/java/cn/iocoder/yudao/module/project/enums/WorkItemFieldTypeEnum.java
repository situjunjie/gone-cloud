package cn.iocoder.yudao.module.project.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum WorkItemFieldTypeEnum implements ArrayValuable<String> {

    TEXT("TEXT", false),
    NUMBER("NUMBER", false),
    DATE_TIME("DATE_TIME", false),
    SINGLE_SELECT("SINGLE_SELECT", true),
    MULTI_SELECT("MULTI_SELECT", true),
    USER("USER", false),
    BOOLEAN("BOOLEAN", false);

    public static final String[] ARRAYS = Arrays.stream(values()).map(WorkItemFieldTypeEnum::getValue)
            .toArray(String[]::new);

    private final String value;
    private final boolean select;

    @Override
    public String[] array() {
        return ARRAYS;
    }

    public static WorkItemFieldTypeEnum of(String value) {
        return Arrays.stream(values()).filter(item -> item.value.equals(value)).findFirst().orElse(null);
    }

}
