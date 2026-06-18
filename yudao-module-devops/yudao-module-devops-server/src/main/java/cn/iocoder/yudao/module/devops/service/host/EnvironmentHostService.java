package cn.iocoder.yudao.module.devops.service.host;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostDashboardRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostDetailRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostProcessRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;

import java.util.List;

public interface EnvironmentHostService {

    Long createHost(EnvironmentHostSaveReqVO createReqVO);

    void updateHost(EnvironmentHostSaveReqVO updateReqVO);

    void deleteHost(Long id);

    EnvironmentHostDO getHost(Long id);

    PageResult<EnvironmentHostDO> getHostPage(EnvironmentHostPageReqVO pageReqVO);

    EnvironmentHostDO checkHost(Long id);

    EnvironmentHostDashboardRespVO getDashboard(Long envId);

    EnvironmentHostDetailRespVO getHostDetail(Long id);

    List<EnvironmentHostProcessRespVO> getHostProcesses(Long id, Integer limit);

    EnvironmentHostDO validateHostExists(Long id);

    EnvironmentDO validateHostEnvironment(Long envId);

}
