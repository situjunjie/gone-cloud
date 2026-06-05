package cn.iocoder.yudao.module.devops.dal.mysql.change;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeEnvDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChangeEnvMapper extends BaseMapperX<ChangeEnvDO> {

    default List<ChangeEnvDO> selectListByChangeId(Long changeId) {
        return selectList(ChangeEnvDO::getChangeId, changeId);
    }

    default ChangeEnvDO selectByChangeIdAndApplicationEnvId(Long changeId, Long applicationEnvId) {
        return selectOne(ChangeEnvDO::getChangeId, changeId,
                ChangeEnvDO::getApplicationEnvId, applicationEnvId);
    }

    default int deleteByChangeId(Long changeId) {
        return delete(ChangeEnvDO::getChangeId, changeId);
    }

}
