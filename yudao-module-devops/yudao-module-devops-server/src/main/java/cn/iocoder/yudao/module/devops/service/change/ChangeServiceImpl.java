package cn.iocoder.yudao.module.devops.service.change;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeCodeReviewDiffRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeCodeReviewOperateReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeCreateFromApplicationReqVO;
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
import cn.iocoder.yudao.module.devops.convert.change.ChangeConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;
import cn.iocoder.yudao.module.devops.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeMapper;
import cn.iocoder.yudao.module.devops.enums.ApprovalStatusEnum;
import cn.iocoder.yudao.module.devops.enums.ChangeCodeReviewStatusEnum;
import cn.iocoder.yudao.module.devops.enums.ChangeEnvMountStatusEnum;
import cn.iocoder.yudao.module.devops.enums.ChangeStatusEnum;
import cn.iocoder.yudao.module.devops.enums.MergeStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineStatusEnum;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderAuthTypeEnum;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderTypeEnum;
import cn.iocoder.yudao.module.devops.framework.git.GitCommandException;
import cn.iocoder.yudao.module.devops.framework.git.GitMergeResult;
import cn.iocoder.yudao.module.devops.framework.git.GitWorkspacePrepareResult;
import cn.iocoder.yudao.module.devops.framework.git.GitWorkspaceService;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.dto.RepositoryProviderCompareDiffDTO;
import jakarta.annotation.Resource;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

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
    private static final String GITLAB_REF_HEADS_PREFIX = "refs/heads/";
    private static final String GITLAB_ZERO_COMMIT_SHA = "0000000000000000000000000000000000000000";
    private static final String DIFF_CHANGE_TYPE_ADDED = "ADDED";
    private static final String DIFF_CHANGE_TYPE_DELETED = "DELETED";
    private static final String DIFF_CHANGE_TYPE_RENAMED = "RENAMED";
    private static final String DIFF_CHANGE_TYPE_MODIFIED = "MODIFIED";

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
    @Resource
    private GitWorkspaceService gitWorkspaceService;
    @Resource
    private CacheManager cacheManager;

    @Override
    public Long createChange(ChangeSaveReqVO createReqVO) {
        validateApplicationExists(createReqVO.getAppId());
        validateChangeUnique(null, createReqVO.getAppId(), createReqVO.getChangeKey(), createReqVO.getBranchName());

        ChangeDO change = ChangeConvert.INSTANCE.convert(createReqVO);
        change.setStatus(ChangeStatusEnum.ACTIVE.getStatus());
        fillDefaultReviewTest(change);
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
        fillDefaultReviewTest(change);
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
        evictCurrentRunCacheByChangeId(updateReqVO.getId());
    }

    @Override
    public void setTester(ChangeSetTesterReqVO setTesterReqVO) {
        ChangeDO change = validateChangeExists(setTesterReqVO.getId());
        validateActive(change);
        changeMapper.updateTesterById(setTesterReqVO.getId(), setTesterReqVO.getTesterUserId(), LocalDateTime.now());
        evictCurrentRunCacheByChangeId(setTesterReqVO.getId());
    }

    @Override
    public void setCodeReviewer(ChangeSetCodeReviewerReqVO setCodeReviewerReqVO) {
        ChangeDO change = validateChangeExists(setCodeReviewerReqVO.getId());
        validateActive(change);
        changeMapper.updateCodeReviewerById(setCodeReviewerReqVO.getId(),
                setCodeReviewerReqVO.getCodeReviewerUserId(), LocalDateTime.now());
        evictCurrentRunCacheByChangeId(setCodeReviewerReqVO.getId());
    }

    @Override
    public void passTest(ChangeTestOperateReqVO reqVO, Long userId) {
        ChangeDO change = validateChangeExists(reqVO.getId());
        validateActive(change);
        validateTester(change, userId);
        if (StrUtil.isBlank(change.getLatestCommitSha())) {
            throw exception(CHANGE_LATEST_COMMIT_NOT_EXISTS);
        }
        changeMapper.updateTestPassedById(reqVO.getId(), change.getLatestCommitSha(), LocalDateTime.now());
        evictCurrentRunCacheByChangeId(reqVO.getId());
    }

    @Override
    public void resetTest(ChangeTestOperateReqVO reqVO, Long userId) {
        ChangeDO change = validateChangeExists(reqVO.getId());
        validateActive(change);
        validateTester(change, userId);
        changeMapper.updateTestResetById(reqVO.getId(), LocalDateTime.now());
        evictCurrentRunCacheByChangeId(reqVO.getId());
    }

    @Override
    public ChangeCodeReviewDiffRespVO getCodeReviewDiff(Long id) {
        ChangeDO change = validateChangeExists(id);
        validateActive(change);
        ApplicationDO application = validateApplicationExists(change.getAppId());
        String compareBaseRef = firstNotBlank(change.getCodeReviewPassedCommitSha(),
                firstNotBlank(change.getSourceBaseBranchName(), application.getDefaultBranchName()));
        String compareTargetRef = firstNotBlank(change.getLatestCommitSha(), change.getBranchName());
        List<RepositoryProviderCompareDiffDTO> diffList = repositoryProviderService.compareRepositoryDiff(
                application.getRepositoryProviderId(), application.getRepoIdentifier(), compareBaseRef, compareTargetRef);
        return buildCodeReviewDiffRespVO(change, compareBaseRef, compareTargetRef, diffList);
    }

    @Override
    public void startCodeReview(ChangeCodeReviewOperateReqVO reqVO, Long userId) {
        ChangeDO change = validateChangeExists(reqVO.getId());
        validateActive(change);
        if (ChangeCodeReviewStatusEnum.APPROVED.getStatus().equals(change.getCodeReviewStatus())) {
            return;
        }
        changeMapper.updateCodeReviewInProgressById(reqVO.getId(), resolveCodeReviewerUserId(change, userId),
                LocalDateTime.now());
        evictCurrentRunCacheByChangeId(reqVO.getId());
    }

    @Override
    public void approveCodeReview(ChangeCodeReviewOperateReqVO reqVO, Long userId) {
        ChangeDO change = validateChangeExists(reqVO.getId());
        validateActive(change);
        validateCodeReviewer(change, userId);
        if (StrUtil.isBlank(change.getLatestCommitSha())) {
            throw exception(CHANGE_LATEST_COMMIT_NOT_EXISTS);
        }
        changeMapper.updateCodeReviewApprovedById(reqVO.getId(), change.getCodeReviewerUserId(),
                change.getLatestCommitSha(), LocalDateTime.now());
        evictCurrentRunCacheByChangeId(reqVO.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChange(Long id) {
        validateChangeExists(id);
        List<ChangeEnvDO> changeEnvs = changeEnvMapper.selectListByChangeId(id);
        changeEnvMapper.deleteByChangeId(id);
        changeMapper.deleteById(id);
        evictCurrentRunCacheByChangeEnvs(changeEnvs);
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
        evictCurrentRunCacheByChangeId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finalizePublishedChanges(List<Long> changeIds, String deployBranchName) {
        List<ChangeDO> changes = changeIds.stream()
                .filter(Objects::nonNull)
                .map(this::validateChangeExists)
                .toList();
        if (changes.isEmpty()) {
            return;
        }
        ChangeDO referenceChange = changes.get(0);
        ApplicationDO application = validateApplicationExists(referenceChange.getAppId());
        String baselineBranch = firstNotBlank(referenceChange.getSourceBaseBranchName(), application.getDefaultBranchName());
        if (StrUtil.isBlank(baselineBranch)) {
            throw exception(CHANGE_BASELINE_BRANCH_REQUIRED);
        }
        if (StrUtil.isBlank(deployBranchName)) {
            throw exception(CHANGE_BRANCH_REQUIRED);
        }
        boolean needMerge = false;
        for (ChangeDO change : changes) {
            if (ChangeStatusEnum.RELEASED.getStatus().equals(change.getStatus())) {
                continue;
            }
            validateActive(change);
            needMerge = true;
        }
        if (needMerge) {
            RepositoryProviderDO provider = validateChangeFinalizeRepositoryProvider(application.getRepositoryProviderId());
            mergeDeployBranchIntoBaseline(referenceChange, application, provider, baselineBranch, deployBranchName);
        }
        LocalDateTime now = LocalDateTime.now();
        for (ChangeDO change : changes) {
            if (!ChangeStatusEnum.RELEASED.getStatus().equals(change.getStatus())) {
                changeMapper.updateReleasedById(change.getId(), now, now, now);
                evictCurrentRunCacheByChangeId(change.getId());
            }
            deleteRemoteChangeBranch(change, application);
        }
        deleteRemoteBranch(application, deployBranchName);
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
        evictCurrentRunCacheByChangeId(discardReqVO.getId());
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
    @CacheEvict(value = RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN, key = "#mountReqVO.applicationEnvId")
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
    @CacheEvict(value = RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN, key = "#unmountReqVO.applicationEnvId")
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

    @Override
    public boolean syncLatestCommitFromGitLabPushHook(Long repositoryProviderId,
                                                      RepositoryProviderGitLabPushHookReqVO reqVO) {
        if (reqVO == null || !isGitLabPushEvent(reqVO)) {
            return false;
        }
        String branchName = parseBranchName(reqVO.getRef());
        String commitSha = firstNotBlank(reqVO.getCheckoutSha(), reqVO.getAfter());
        if (StrUtil.isBlank(branchName) || StrUtil.isBlank(commitSha)
                || GITLAB_ZERO_COMMIT_SHA.equals(commitSha)) {
            return false;
        }
        String repoIdentifier = reqVO.getProject() == null ? null : reqVO.getProject().getPathWithNamespace();
        if (StrUtil.isBlank(repoIdentifier)) {
            return false;
        }

        RepositoryProviderDO provider = repositoryProviderService.validateRepositoryProviderExists(repositoryProviderId);
        if (!RepositoryProviderTypeEnum.GITLAB.getProviderType().equals(provider.getProviderType())) {
            throw exception(REPOSITORY_PROVIDER_TYPE_NOT_SUPPORTED);
        }
        ApplicationDO application = applicationMapper
                .selectByRepositoryProviderIdAndRepoIdentifier(repositoryProviderId, repoIdentifier);
        if (application == null) {
            return false;
        }
        ChangeDO change = changeMapper.selectByAppIdAndBranchNameAndStatus(application.getId(), branchName,
                ChangeStatusEnum.ACTIVE.getStatus());
        if (change == null) {
            return false;
        }

        RepositoryProviderGitLabPushHookReqVO.Commit commit = findCommit(reqVO.getCommits(), commitSha);
        String commitMessage = commit == null ? null : StrUtil.subPre(commit.getMessage(), 512);
        LocalDateTime commitAt = parseGitLabCommitTime(commit == null ? null : commit.getTimestamp());
        if (!Objects.equals(commitSha, change.getLatestCommitSha())) {
            changeMapper.updateLatestCommitAndResetReviewTest(change.getId(), commitSha, commitMessage, commitAt,
                    LocalDateTime.now());
            evictCurrentRunCacheByChangeId(change.getId());
            return true;
        }
        ChangeDO updateObj = new ChangeDO();
        updateObj.setId(change.getId());
        updateObj.setLatestCommitSha(commitSha);
        updateObj.setLatestCommitMessage(commitMessage);
        updateObj.setLatestCommitAt(commitAt);
        changeMapper.updateById(updateObj);
        evictCurrentRunCacheByChangeId(change.getId());
        return true;
    }

    private void fillDefaultReviewTest(ChangeDO change) {
        if (change.getTestPassed() == null) {
            change.setTestPassed(0);
        }
        if (change.getCodeReviewStatus() == null) {
            change.setCodeReviewStatus(ChangeCodeReviewStatusEnum.OPEN.getStatus());
        }
    }

    private ChangeCodeReviewDiffRespVO buildCodeReviewDiffRespVO(ChangeDO change, String compareBaseRef,
                                                                 String compareTargetRef,
                                                                 List<RepositoryProviderCompareDiffDTO> diffList) {
        ChangeCodeReviewDiffRespVO respVO = new ChangeCodeReviewDiffRespVO();
        respVO.setChangeId(change.getId());
        respVO.setAppId(change.getAppId());
        respVO.setBranchName(change.getBranchName());
        respVO.setSourceBaseBranchName(change.getSourceBaseBranchName());
        respVO.setCompareBaseRef(compareBaseRef);
        respVO.setCompareTargetRef(compareTargetRef);
        respVO.setCodeReviewPassedCommitSha(change.getCodeReviewPassedCommitSha());
        respVO.setLatestCommitSha(change.getLatestCommitSha());
        respVO.setFiles(diffList.stream().map(this::buildFileDiffRespVO).toList());
        return respVO;
    }

    private ChangeCodeReviewDiffRespVO.FileDiff buildFileDiffRespVO(RepositoryProviderCompareDiffDTO diff) {
        ChangeCodeReviewDiffRespVO.FileDiff fileDiff = new ChangeCodeReviewDiffRespVO.FileDiff();
        fileDiff.setOldPath(diff.getOldPath());
        fileDiff.setNewPath(diff.getNewPath());
        fileDiff.setPath(firstNotBlank(diff.getNewPath(), diff.getOldPath()));
        fileDiff.setNewFile(diff.getNewFile());
        fileDiff.setDeletedFile(diff.getDeletedFile());
        fileDiff.setRenamedFile(diff.getRenamedFile());
        fileDiff.setChangeType(resolveDiffChangeType(diff));
        fileDiff.setDiff(diff.getDiff());
        return fileDiff;
    }

    private String resolveDiffChangeType(RepositoryProviderCompareDiffDTO diff) {
        if (Boolean.TRUE.equals(diff.getNewFile())) {
            return DIFF_CHANGE_TYPE_ADDED;
        }
        if (Boolean.TRUE.equals(diff.getDeletedFile())) {
            return DIFF_CHANGE_TYPE_DELETED;
        }
        if (Boolean.TRUE.equals(diff.getRenamedFile())) {
            return DIFF_CHANGE_TYPE_RENAMED;
        }
        return DIFF_CHANGE_TYPE_MODIFIED;
    }

    private Long resolveCodeReviewerUserId(ChangeDO change, Long userId) {
        return change.getCodeReviewerUserId() == null ? userId : change.getCodeReviewerUserId();
    }

    private void validateTester(ChangeDO change, Long userId) {
        if (change.getTesterUserId() == null) {
            throw exception(CHANGE_TESTER_NOT_ASSIGNED);
        }
        if (!Objects.equals(change.getTesterUserId(), userId)) {
            throw exception(CHANGE_TESTER_NOT_MATCH);
        }
    }

    private void validateCodeReviewer(ChangeDO change, Long userId) {
        if (change.getCodeReviewerUserId() == null) {
            throw exception(CHANGE_CODE_REVIEWER_NOT_ASSIGNED);
        }
        if (!Objects.equals(change.getCodeReviewerUserId(), userId)) {
            throw exception(CHANGE_CODE_REVIEWER_NOT_MATCH);
        }
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

    private void evictCurrentRunCacheByChangeId(Long changeId) {
        evictCurrentRunCacheByChangeEnvs(changeEnvMapper.selectListByChangeId(changeId));
    }

    private void evictCurrentRunCacheByChangeEnvs(List<ChangeEnvDO> changeEnvs) {
        if (changeEnvs == null || changeEnvs.isEmpty()) {
            return;
        }
        Cache cache = cacheManager.getCache(RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN);
        if (cache == null) {
            return;
        }
        changeEnvs.stream()
                .map(ChangeEnvDO::getApplicationEnvId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(cache::evict);
    }

    private boolean isGitLabPushEvent(RepositoryProviderGitLabPushHookReqVO reqVO) {
        return "push".equals(reqVO.getObjectKind()) || "push".equals(reqVO.getEventName());
    }

    private String parseBranchName(String ref) {
        if (StrUtil.isBlank(ref) || !ref.startsWith(GITLAB_REF_HEADS_PREFIX)) {
            return null;
        }
        return StrUtil.removePrefix(ref, GITLAB_REF_HEADS_PREFIX);
    }

    private RepositoryProviderGitLabPushHookReqVO.Commit findCommit(
            List<RepositoryProviderGitLabPushHookReqVO.Commit> commits, String commitSha) {
        if (commits == null) {
            return null;
        }
        return commits.stream()
                .filter(commit -> Objects.equals(commitSha, commit.getId()))
                .findFirst()
                .orElse(commits.isEmpty() ? null : commits.get(commits.size() - 1));
    }

    private LocalDateTime parseGitLabCommitTime(String timestamp) {
        if (StrUtil.isBlank(timestamp)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(timestamp).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(timestamp);
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private String firstNotBlank(String first, String second) {
        return StrUtil.isNotBlank(first) ? first : second;
    }

    private RepositoryProviderDO validateChangeFinalizeRepositoryProvider(Long repositoryProviderId) {
        RepositoryProviderDO provider = repositoryProviderService.validateRepositoryProviderExists(repositoryProviderId);
        if (!RepositoryProviderTypeEnum.GITLAB.getProviderType().equals(provider.getProviderType())) {
            throw exception(REPOSITORY_PROVIDER_TYPE_NOT_SUPPORTED);
        }
        if (!RepositoryProviderAuthTypeEnum.ACCESS_TOKEN.getAuthType().equals(provider.getAuthType())) {
            throw exception(REPOSITORY_PROVIDER_AUTH_TYPE_NOT_SUPPORTED);
        }
        if (StrUtil.isBlank(provider.getAccessToken())) {
            throw exception(REPOSITORY_PROVIDER_ACCESS_TOKEN_REQUIRED);
        }
        return provider;
    }

    private void mergeDeployBranchIntoBaseline(ChangeDO change, ApplicationDO application, RepositoryProviderDO provider,
                                               String baselineBranch, String deployBranchName) {
        if (StrUtil.isBlank(application.getRepoUrl())) {
            throw exception(CHANGE_BRANCH_MERGE_FAIL, "应用代码库地址不能为空");
        }
        GitWorkspacePrepareResult workspace = null;
        try {
            workspace = gitWorkspaceService.prepareWorkspace(change.getId(), application.getRepoUrl(),
                    provider.getAccessToken(), baselineBranch, baselineBranch);
            String branchCommitSha = gitWorkspaceService.resolveRemoteBranchCommit(workspace.getWorkspaceKey(), deployBranchName);
            GitMergeResult mergeResult = gitWorkspaceService.merge(workspace.getWorkspaceKey(), branchCommitSha,
                    "Merge deploy branch " + deployBranchName + " into " + baselineBranch);
            if (!GitMergeResult.STATUS_SUCCESS.equals(mergeResult.getStatus())) {
                throw exception(CHANGE_BRANCH_MERGE_FAIL, StrUtil.blankToDefault(mergeResult.getOutput(), "代码合并冲突"));
            }
            gitWorkspaceService.pushDeployBranch(workspace.getWorkspaceKey(), baselineBranch);
        } catch (GitCommandException ex) {
            throw exception(CHANGE_BRANCH_MERGE_FAIL, sanitizeGitError(ex));
        } finally {
            if (workspace != null) {
                gitWorkspaceService.cleanup(workspace.getWorkspaceKey());
            }
        }
    }

    private void deleteRemoteChangeBranch(ChangeDO change) {
        ApplicationDO application = validateApplicationExists(change.getAppId());
        deleteRemoteChangeBranch(change, application);
    }

    private void deleteRemoteChangeBranch(ChangeDO change, ApplicationDO application) {
        if (StrUtil.isBlank(change.getBranchName())) {
            return;
        }
        repositoryProviderService.deleteRepositoryBranch(application.getRepositoryProviderId(),
                application.getRepoIdentifier(), change.getBranchName());
    }

    private void deleteRemoteBranch(ApplicationDO application, String branchName) {
        if (StrUtil.isBlank(branchName)) {
            return;
        }
        repositoryProviderService.deleteRepositoryBranch(application.getRepositoryProviderId(),
                application.getRepoIdentifier(), branchName);
    }

    private String sanitizeGitError(GitCommandException ex) {
        return StrUtil.subPre(firstNotBlank(ex.getOutput(), ex.getMessage()), 512);
    }

}
