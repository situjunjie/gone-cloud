package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 制品仓库连接检测状态。
 */
@Getter
@AllArgsConstructor
public enum ArtifactRegistryCheckStatusEnum {

    SUCCESS(0),
    FAIL(1);

    private final Integer status;

}
