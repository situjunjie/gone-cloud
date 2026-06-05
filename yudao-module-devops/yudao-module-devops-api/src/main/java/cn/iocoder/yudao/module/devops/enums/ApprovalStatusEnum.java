package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 审批状态。
 */
@Getter
@AllArgsConstructor
public enum ApprovalStatusEnum {

    NONE(0),
    PENDING(1),
    APPROVED(2),
    REJECTED(3);

    private final Integer status;

}
