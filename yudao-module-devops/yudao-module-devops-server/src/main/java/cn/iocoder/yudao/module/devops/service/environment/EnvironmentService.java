package cn.iocoder.yudao.module.devops.service.environment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;

public interface EnvironmentService {

    Long createEnvironment(EnvironmentSaveReqVO createReqVO);

    void updateEnvironment(EnvironmentSaveReqVO updateReqVO);

    void deleteEnvironment(Long id);

    EnvironmentDO getEnvironment(Long id);

    PageResult<EnvironmentDO> getEnvironmentPage(EnvironmentPageReqVO pageReqVO);

    EnvironmentDO validateEnvironmentExists(Long id);

}
