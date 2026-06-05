package cn.iocoder.yudao.module.devops.service.application;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationEnvRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationSaveReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationUpdateEnvsReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;

import java.util.List;

public interface ApplicationService {

    Long createApplication(ApplicationSaveReqVO createReqVO);

    void updateApplication(ApplicationSaveReqVO updateReqVO);

    void deleteApplication(Long id);

    ApplicationDO getApplication(Long id);

    PageResult<ApplicationDO> getApplicationPage(ApplicationPageReqVO pageReqVO);

    void updateApplicationEnvs(ApplicationUpdateEnvsReqVO updateReqVO);

    List<ApplicationEnvRespVO> getApplicationEnvList(Long appId);

    ApplicationDO validateApplicationExists(Long id);

}
