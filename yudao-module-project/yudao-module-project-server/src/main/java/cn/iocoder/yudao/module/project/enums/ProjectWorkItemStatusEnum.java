package cn.iocoder.yudao.module.project.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum ProjectWorkItemStatusEnum implements ArrayValuable<String> {

    PENDING("PENDING", "待处理"),
    DESIGNING("DESIGNING", "设计中"),
    DEVELOPING("DEVELOPING", "开发中"),
    TESTING("TESTING", "测试中"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELED("CANCELED", "已取消");

    public static final String[] ARRAYS = Arrays.stream(values()).map(ProjectWorkItemStatusEnum::getValue)
            .toArray(String[]::new);

    private final String value;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
