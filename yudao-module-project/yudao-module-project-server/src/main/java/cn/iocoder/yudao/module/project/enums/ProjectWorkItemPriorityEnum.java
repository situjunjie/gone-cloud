package cn.iocoder.yudao.module.project.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum ProjectWorkItemPriorityEnum implements ArrayValuable<String> {

    URGENT("URGENT", "紧急"),
    HIGH("HIGH", "高"),
    MEDIUM("MEDIUM", "中"),
    LOW("LOW", "低");

    public static final String[] ARRAYS = Arrays.stream(values()).map(ProjectWorkItemPriorityEnum::getValue)
            .toArray(String[]::new);

    private final String value;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
