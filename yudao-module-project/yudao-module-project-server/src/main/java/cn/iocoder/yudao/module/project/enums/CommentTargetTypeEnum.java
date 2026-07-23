package cn.iocoder.yudao.module.project.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum CommentTargetTypeEnum implements ArrayValuable<String> {

    WORK_ITEM("WORK_ITEM", "工作项");

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(CommentTargetTypeEnum::getValue).toArray(String[]::new);

    private final String value;
    private final String name;

    public static CommentTargetTypeEnum of(String value) {
        return Arrays.stream(values()).filter(item -> item.value.equals(value)).findFirst().orElse(null);
    }

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
