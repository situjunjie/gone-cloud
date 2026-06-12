package cn.iocoder.yudao.module.devops.service.buildhost;

import cn.iocoder.yudao.module.devops.dal.dataobject.buildhost.BuildHostDO;

/**
 * 构建主机选择器。
 *
 * <p>按 label / 容量挑选承载本次构建的主机。v1 实现固定返回默认 SSH 构建主机；
 * 接口预留 {@link BuildHostSelectCriteria}（label / 类型 / 容量），供后续多机调度扩展，引擎调用方零改动。
 */
public interface BuildHostSelector {

    /**
     * 选择一台构建主机。
     *
     * @param criteria 选择条件，可为 {@link BuildHostSelectCriteria#none()}
     * @return 选中的构建主机
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 无可用主机时抛出 BUILD_HOST_NO_AVAILABLE
     */
    BuildHostDO select(BuildHostSelectCriteria criteria);

    /**
     * 选择默认构建主机（无任何约束）。
     */
    default BuildHostDO selectDefault() {
        return select(BuildHostSelectCriteria.none());
    }

}
