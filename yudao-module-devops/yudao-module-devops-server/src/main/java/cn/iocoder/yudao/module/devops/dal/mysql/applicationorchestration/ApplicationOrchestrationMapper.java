package cn.iocoder.yudao.module.devops.dal.mysql.applicationorchestration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.dal.dataobject.applicationorchestration.ApplicationOrchestrationDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface ApplicationOrchestrationMapper extends BaseMapperX<ApplicationOrchestrationDO> {

    default ApplicationOrchestrationDO selectByAppIdAndType(Long appId, String orchestrationType) {
        return selectOne(new LambdaQueryWrapperX<ApplicationOrchestrationDO>()
                .eq(ApplicationOrchestrationDO::getAppId, appId)
                .eq(ApplicationOrchestrationDO::getOrchestrationType, orchestrationType));
    }

    default int updateByIdAndUpdateTime(ApplicationOrchestrationDO updateObj, LocalDateTime expectedUpdateTime) {
        return update(updateObj, new LambdaUpdateWrapper<ApplicationOrchestrationDO>()
                .eq(ApplicationOrchestrationDO::getId, updateObj.getId())
                .eq(ApplicationOrchestrationDO::getUpdateTime, expectedUpdateTime));
    }

}
