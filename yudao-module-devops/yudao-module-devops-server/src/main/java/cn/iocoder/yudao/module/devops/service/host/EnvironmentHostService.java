package cn.iocoder.yudao.module.devops.service.host;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;

public interface EnvironmentHostService {

    Long createHost(EnvironmentHostSaveReqVO createReqVO);

    void updateHost(EnvironmentHostSaveReqVO updateReqVO);

    void deleteHost(Long id);

    EnvironmentHostDO getHost(Long id);

    PageResult<EnvironmentHostDO> getHostPage(EnvironmentHostPageReqVO pageReqVO);

    EnvironmentHostDO checkHost(Long id);

    EnvironmentHostDO validateHostExists(Long id);

}
