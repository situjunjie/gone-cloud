package cn.iocoder.yudao.module.project.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum WorklogWorkItemScopeEnum implements ArrayValuable<String> {

    SELF("SELF"),
    DESCENDANTS("DESCENDANTS"),
    SELF_AND_DESCENDANTS("SELF_AND_DESCENDANTS");

    public static final String[] ARRAYS = Arrays.stream(values()).map(WorklogWorkItemScopeEnum::getValue)
            .toArray(String[]::new);

    private final String value;

    @Override
    public String[] array() {
        return ARRAYS;
    }

    public static WorklogWorkItemScopeEnum of(String value) {
        return Arrays.stream(values()).filter(item -> item.value.equals(value)).findFirst().orElse(null);
    }

}
