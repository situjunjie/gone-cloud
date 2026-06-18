package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 主机连接检测状态。
 */
@Getter
@AllArgsConstructor
public enum HostCheckStatusEnum {

    SUCCESS(0, "成功"),
    FAIL(1, "失败");

    private final Integer status;
    private final String name;

}
