package cn.iocoder.yudao.module.devops.service.change;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeCreateFromApplicationReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeCodeReviewDiffRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeCodeReviewOperateReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeDiscardReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeEnvMountReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeEnvRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeEnvUnmountReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangePageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeSaveReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeSetCodeReviewerReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeSetTesterReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeTestOperateReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo.RepositoryProviderGitLabPushHookReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeDO;

import java.util.List;

public interface ChangeService {

    Long createChange(ChangeSaveReqVO createReqVO);

    Long createChangeFromApplication(ChangeCreateFromApplicationReqVO createReqVO, Long userId);

    void updateChange(ChangeSaveReqVO updateReqVO);

    void setTester(ChangeSetTesterReqVO setTesterReqVO);

    void setCodeReviewer(ChangeSetCodeReviewerReqVO setCodeReviewerReqVO);

    void passTest(ChangeTestOperateReqVO reqVO, Long userId);

    void resetTest(ChangeTestOperateReqVO reqVO, Long userId);

    ChangeCodeReviewDiffRespVO getCodeReviewDiff(Long id);

    void startCodeReview(ChangeCodeReviewOperateReqVO reqVO, Long userId);

    void approveCodeReview(ChangeCodeReviewOperateReqVO reqVO, Long userId);

    void deleteChange(Long id);

    void releaseChange(Long id);

    /**
     * 发布完成后收尾：将本次发布部署分支合并回基准分支，标记参与变更为已发布，
     * 并删除参与发布的变更分支和本次部署分支。
     *
     * @param changeIds         参与发布的变更编号列表
     * @param deployBranchName  本次发布部署分支名称
     */
    void finalizePublishedChanges(List<Long> changeIds, String deployBranchName);

    void discardChange(ChangeDiscardReqVO discardReqVO);

    ChangeDO getChange(Long id);

    PageResult<ChangeDO> getChangePage(ChangePageReqVO pageReqVO);

    Long mountChangeEnv(ChangeEnvMountReqVO mountReqVO, Long userId);

    void unmountChangeEnv(ChangeEnvUnmountReqVO unmountReqVO, Long userId);

    List<ChangeEnvRespVO> getChangeEnvList(Long changeId);

    boolean syncLatestCommitFromGitLabPushHook(Long repositoryProviderId, RepositoryProviderGitLabPushHookReqVO reqVO);

}
