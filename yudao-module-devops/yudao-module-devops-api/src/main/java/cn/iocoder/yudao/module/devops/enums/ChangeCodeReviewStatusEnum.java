package cn.iocoder.yudao.module.devops.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * DevOps 变更代码审核状态。
 */
@Getter
@AllArgsConstructor
public enum ChangeCodeReviewStatusEnum implements ArrayValuable<Integer> {

    OPEN(0),
    IN_PROGRESS(1),
    APPROVED(2);

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(ChangeCodeReviewStatusEnum::getStatus)
            .toArray(Integer[]::new);

    private final Integer status;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
