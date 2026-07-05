package cn.iocoder.yudao.module.project.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum WorkItemQueryViewScopeEnum implements ArrayValuable<String> {

    SYSTEM("SYSTEM", "系统级"),
    USER("USER", "用户级");

    public static final String[] ARRAYS = Arrays.stream(values()).map(WorkItemQueryViewScopeEnum::getValue)
            .toArray(String[]::new);

    private final String value;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
