package cn.iocoder.yudao.module.project.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum ProjectWorkItemCategoryEnum implements ArrayValuable<String> {

    REQUIREMENT("REQUIREMENT", "需求"),
    WORK_ITEM("WORK_ITEM", "工作项"),
    TASK("TASK", "任务"),
    BUG("BUG", "缺陷");

    public static final String[] ARRAYS = Arrays.stream(values()).map(ProjectWorkItemCategoryEnum::getValue)
            .toArray(String[]::new);

    private final String value;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
