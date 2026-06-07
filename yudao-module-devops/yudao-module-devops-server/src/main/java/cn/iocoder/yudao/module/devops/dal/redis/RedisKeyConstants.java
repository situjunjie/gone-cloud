package cn.iocoder.yudao.module.devops.dal.redis;

import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseCurrentRunRespVO;

/**
 * DevOps Redis Key 枚举类。
 */
public interface RedisKeyConstants {

    /**
     * 应用发布当前流水线运行的缓存，使用 Spring Cache 操作。
     * <p>
     * KEY 格式：devops:application_release_current_run:${applicationEnvId}
     * VALUE 数据类型：String(JSON)，即 {@link ApplicationReleaseCurrentRunRespVO}
     */
    String APPLICATION_RELEASE_CURRENT_RUN = "devops:application_release_current_run#5s";

}
