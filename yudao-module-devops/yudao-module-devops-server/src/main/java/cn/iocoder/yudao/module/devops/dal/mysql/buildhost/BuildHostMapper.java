package cn.iocoder.yudao.module.devops.dal.mysql.buildhost;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.dal.dataobject.buildhost.BuildHostDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BuildHostMapper extends BaseMapperX<BuildHostDO> {

    default BuildHostDO selectByName(String name) {
        return selectOne(BuildHostDO::getName, name);
    }

    /**
     * 查询所有启用且状态开启的构建主机，供 {@code BuildHostSelector} 调度。
     */
    default List<BuildHostDO> selectEnabledList() {
        return selectList(new LambdaQueryWrapperX<BuildHostDO>()
                .eq(BuildHostDO::getEnabled, Boolean.TRUE)
                .eq(BuildHostDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                .orderByAsc(BuildHostDO::getId));
    }

    /**
     * 按类型查询启用的构建主机。
     */
    default List<BuildHostDO> selectEnabledListByType(String type) {
        return selectList(new LambdaQueryWrapperX<BuildHostDO>()
                .eqIfPresent(BuildHostDO::getType, type)
                .eq(BuildHostDO::getEnabled, Boolean.TRUE)
                .eq(BuildHostDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                .orderByAsc(BuildHostDO::getId));
    }

}
