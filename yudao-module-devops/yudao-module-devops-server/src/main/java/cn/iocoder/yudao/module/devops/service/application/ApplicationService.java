package cn.iocoder.yudao.module.devops.service.application;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationEnvRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseCurrentRunRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseEnvDetailRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseEnvTabRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationSaveReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseSubmitBranchReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseSubmitBranchRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseUploadImageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseUploadImageRespVO;
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

    List<ApplicationReleaseEnvTabRespVO> getApplicationReleaseEnvTabs(Long appId);

    ApplicationReleaseEnvDetailRespVO getApplicationReleaseEnvDetail(Long applicationEnvId);

    ApplicationReleaseCurrentRunRespVO getApplicationReleaseCurrentRun(Long applicationEnvId);

    ApplicationReleaseSubmitBranchRespVO submitApplicationReleaseBranch(ApplicationReleaseSubmitBranchReqVO reqVO,
                                                                       Long userId);

    ApplicationReleaseUploadImageRespVO uploadApplicationReleaseImage(ApplicationReleaseUploadImageReqVO reqVO,
                                                                     Long userId);

    ApplicationDO validateApplicationExists(Long id);

}
