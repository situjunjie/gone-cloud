package cn.iocoder.yudao.module.devops.service.change;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeCreateFromApplicationReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeDiscardReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeEnvMountReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeEnvRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeEnvUnmountReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangePageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeSaveReqVO;
import cn.iocoder.yudao.module.devops.convert.change.ChangeConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeEnvDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeMapper;
import cn.iocoder.yudao.module.devops.enums.ApprovalStatusEnum;
import cn.iocoder.yudao.module.devops.enums.ChangeEnvMountStatusEnum;
import cn.iocoder.yudao.module.devops.enums.ChangeStatusEnum;
import cn.iocoder.yudao.module.devops.enums.MergeStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineStatusEnum;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.*;

/**
 * DevOps 变更 Service 实现类。
 */
@Service
@Validated
public class ChangeServiceImpl implements ChangeService {

    private static final String APPLICATION_CHANGE_BRANCH_PREFIX = "feat/";
    private static final int CHANGE_KEY_MAX_LENGTH = 64;

    @Resource
    private ChangeMapper changeMapper;
    @Resource
    private ChangeEnvMapper changeEnvMapper;
    @Resource
    private ApplicationMapper applicationMapper;
    @Resource
    private ApplicationEnvMapper applicationEnvMapper;
    @Resource
    private RepositoryProviderService repositoryProviderService;

    @Override
    public Long createChange(ChangeSaveReqVO createReqVO) {
        validateApplicationExists(createReqVO.getAppId());
        validateChangeUnique(null, createReqVO.getAppId(), createReqVO.getChangeKey(), createReqVO.getBranchName());

        ChangeDO change = ChangeConvert.INSTANCE.convert(createReqVO);
        change.setStatus(ChangeStatusEnum.ACTIVE.getStatus());
        changeMapper.insert(change);
        return change.getId();
    }

    @Override
    public Long createChangeFromApplication(ChangeCreateFromApplicationReqVO createReqVO, Long userId) {
        ApplicationDO application = validateApplicationExists(createReqVO.getAppId());
        String branchName = buildApplicationChangeBranchName(createReqVO.getBranchSlug(), createReqVO.getOpenTimestamp());
        validateGitBranchName(branchName);
        String changeKey = buildApplicationChangeKey(application.getAppKey(), createReqVO.getOpenTimestamp());
        validateChangeUnique(null, createReqVO.getAppId(), changeKey, branchName);
        repositoryProviderService.createRepositoryBranch(application.getRepositoryProviderId(),
                application.getRepoIdentifier(), branchName, application.getDefaultBranchName());

        ChangeDO change = new ChangeDO();
        change.setAppId(createReqVO.getAppId());
        change.setChangeKey(changeKey);
        change.setTitle(createReqVO.getTitle());
        change.setBranchName(branchName);
        change.setSourceBaseBranchName(application.getDefaultBranchName());
        change.setOwnerUserId(userId);
        change.setStatus(ChangeStatusEnum.ACTIVE.getStatus());
        changeMapper.insert(change);
        return change.getId();
    }

    @Override
    public void updateChange(ChangeSaveReqVO updateReqVO) {
        ChangeDO change = validateChangeExists(updateReqVO.getId());
        validateActive(change);
        validateApplicationExists(updateReqVO.getAppId());
        validateChangeUnique(updateReqVO.getId(), updateReqVO.getAppId(), updateReqVO.getChangeKey(), updateReqVO.getBranchName());

        ChangeDO updateObj = ChangeConvert.INSTANCE.convert(updateReqVO);
        changeMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChange(Long id) {
        validateChangeExists(id);
        changeEnvMapper.deleteByChangeId(id);
        changeMapper.deleteById(id);
    }

    @Override
    public void releaseChange(Long id) {
        ChangeDO change = validateChangeExists(id);
        validateActive(change);

        ChangeDO updateObj = new ChangeDO();
        updateObj.setId(id);
        updateObj.setStatus(ChangeStatusEnum.RELEASED.getStatus());
        updateObj.setReleasedAt(LocalDateTime.now());
        changeMapper.updateById(updateObj);
    }

    @Override
    public void discardChange(ChangeDiscardReqVO discardReqVO) {
        ChangeDO change = validateChangeExists(discardReqVO.getId());
        validateActive(change);

        ChangeDO updateObj = new ChangeDO();
        updateObj.setId(discardReqVO.getId());
        updateObj.setStatus(ChangeStatusEnum.DISCARDED.getStatus());
        updateObj.setDiscardedAt(LocalDateTime.now());
        updateObj.setDiscardReason(discardReqVO.getDiscardReason());
        changeMapper.updateById(updateObj);
    }

    @Override
    public ChangeDO getChange(Long id) {
        return changeMapper.selectById(id);
    }

    @Override
    public PageResult<ChangeDO> getChangePage(ChangePageReqVO pageReqVO) {
        return changeMapper.selectPage(pageReqVO);
    }

    @Override
    public Long mountChangeEnv(ChangeEnvMountReqVO mountReqVO, Long userId) {
        ChangeDO change = validateChangeExists(mountReqVO.getChangeId());
        validateActive(change);
        ApplicationEnvDO applicationEnv = validateApplicationEnvExists(mountReqVO.getApplicationEnvId());
        if (!applicationEnv.getAppId().equals(change.getAppId())) {
            throw exception(APPLICATION_ENV_NOT_EXISTS);
        }
        ChangeEnvDO changeEnv = changeEnvMapper.selectByChangeIdAndApplicationEnvId(
                mountReqVO.getChangeId(), mountReqVO.getApplicationEnvId());
        if (changeEnv != null && ChangeEnvMountStatusEnum.MOUNTED.getStatus().equals(changeEnv.getMountStatus())) {
            throw exception(CHANGE_ENV_DUPLICATE);
        }
        if (changeEnv == null) {
            changeEnv = new ChangeEnvDO();
            changeEnv.setChangeId(mountReqVO.getChangeId());
            changeEnv.setApplicationEnvId(mountReqVO.getApplicationEnvId());
            changeEnv.setLastMergeStatus(MergeStatusEnum.PENDING.getStatus());
            changeEnv.setLastBuildStatus(PipelineStatusEnum.PENDING.getStatus());
            changeEnv.setLastTestStatus(PipelineStatusEnum.PENDING.getStatus());
            changeEnv.setLastDeployStatus(PipelineStatusEnum.PENDING.getStatus());
            changeEnv.setApprovalStatus(ApprovalStatusEnum.NONE.getStatus());
            changeEnv.setIncludedInCurrentSnapshot(false);
            fillMounted(changeEnv, userId);
            changeEnvMapper.insert(changeEnv);
            return changeEnv.getId();
        }
        fillMounted(changeEnv, userId);
        changeEnvMapper.updateById(changeEnv);
        return changeEnv.getId();
    }

    @Override
    public void unmountChangeEnv(ChangeEnvUnmountReqVO unmountReqVO, Long userId) {
        ChangeDO change = validateChangeExists(unmountReqVO.getChangeId());
        validateActive(change);
        ChangeEnvDO changeEnv = changeEnvMapper.selectByChangeIdAndApplicationEnvId(
                unmountReqVO.getChangeId(), unmountReqVO.getApplicationEnvId());
        if (changeEnv == null) {
            throw exception(CHANGE_ENV_NOT_EXISTS);
        }

        changeEnv.setMountStatus(ChangeEnvMountStatusEnum.UNMOUNTED.getStatus());
        changeEnv.setUnmountedAt(LocalDateTime.now());
        changeEnv.setUnmountedBy(userId);
        changeEnv.setUnmountedReason(unmountReqVO.getUnmountedReason());
        changeEnv.setIncludedInCurrentSnapshot(false);
        changeEnvMapper.updateById(changeEnv);
    }

    @Override
    public List<ChangeEnvRespVO> getChangeEnvList(Long changeId) {
        return ChangeConvert.INSTANCE.convertEnvList(changeEnvMapper.selectListByChangeId(changeId));
    }

    private ChangeDO validateChangeExists(Long id) {
        ChangeDO change = changeMapper.selectById(id);
        if (change == null) {
            throw exception(CHANGE_NOT_EXISTS);
        }
        return change;
    }

    private ApplicationDO validateApplicationExists(Long appId) {
        ApplicationDO application = applicationMapper.selectById(appId);
        if (application == null) {
            throw exception(APPLICATION_NOT_EXISTS);
        }
        return application;
    }

    private ApplicationEnvDO validateApplicationEnvExists(Long applicationEnvId) {
        ApplicationEnvDO applicationEnv = applicationEnvMapper.selectById(applicationEnvId);
        if (applicationEnv == null) {
            throw exception(APPLICATION_ENV_NOT_EXISTS);
        }
        return applicationEnv;
    }

    private void validateActive(ChangeDO change) {
        if (!ChangeStatusEnum.ACTIVE.getStatus().equals(change.getStatus())) {
            throw exception(CHANGE_STATUS_NOT_ACTIVE);
        }
    }

    private void validateChangeUnique(Long id, Long appId, String changeKey, String branchName) {
        ChangeDO changeKeyChange = changeMapper.selectByAppIdAndChangeKey(appId, changeKey);
        if (changeKeyChange != null && !changeKeyChange.getId().equals(id)) {
            throw exception(CHANGE_KEY_DUPLICATE);
        }
        ChangeDO branchNameChange = changeMapper.selectByAppIdAndBranchName(appId, branchName);
        if (branchNameChange != null && !branchNameChange.getId().equals(id)) {
            throw exception(CHANGE_BRANCH_NAME_DUPLICATE);
        }
    }

    private String buildApplicationChangeBranchName(String branchSlug, Long openTimestamp) {
        return APPLICATION_CHANGE_BRANCH_PREFIX + branchSlug + "-" + openTimestamp;
    }

    private String buildApplicationChangeKey(String appKey, Long openTimestamp) {
        String timestamp = String.valueOf(openTimestamp);
        int maxAppKeyLength = CHANGE_KEY_MAX_LENGTH - timestamp.length() - 1;
        if (appKey.length() > maxAppKeyLength) {
            appKey = appKey.substring(0, maxAppKeyLength);
        }
        return appKey + "-" + timestamp;
    }

    private void validateGitBranchName(String branchName) {
        if (branchName.length() > 128 || !branchName.startsWith(APPLICATION_CHANGE_BRANCH_PREFIX)
                || branchName.startsWith("/") || branchName.endsWith("/") || branchName.endsWith(".")
                || branchName.contains("..") || branchName.contains("//") || branchName.contains("@{")
                || "@".equals(branchName)) {
            throw exception(CHANGE_BRANCH_NAME_INVALID);
        }
        for (int i = 0; i < branchName.length(); i++) {
            char ch = branchName.charAt(i);
            if (ch <= 32 || ch >= 127 || ch == '~' || ch == '^' || ch == ':' || ch == '?'
                    || ch == '*' || ch == '[' || ch == '\\') {
                throw exception(CHANGE_BRANCH_NAME_INVALID);
            }
        }
        for (String pathPart : branchName.split("/")) {
            if (pathPart.isEmpty() || pathPart.startsWith(".") || pathPart.endsWith(".lock")) {
                throw exception(CHANGE_BRANCH_NAME_INVALID);
            }
        }
    }

    private void fillMounted(ChangeEnvDO changeEnv, Long userId) {
        changeEnv.setMountStatus(ChangeEnvMountStatusEnum.MOUNTED.getStatus());
        changeEnv.setMountedAt(LocalDateTime.now());
        changeEnv.setMountedBy(userId);
        changeEnv.setUnmountedAt(null);
        changeEnv.setUnmountedBy(null);
        changeEnv.setUnmountedReason(null);
    }

}
