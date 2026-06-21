package cn.iocoder.yudao.module.devops.service.change;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeCodeReviewDiffRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeCodeReviewOperateReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeCreateFromApplicationReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeEnvMountReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeEnvUnmountReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeSetCodeReviewerReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeSetTesterReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeTestOperateReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo.RepositoryProviderGitLabPushHookReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;
import cn.iocoder.yudao.module.devops.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeMapper;
import cn.iocoder.yudao.module.devops.enums.ChangeCodeReviewStatusEnum;
import cn.iocoder.yudao.module.devops.enums.ChangeStatusEnum;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderTypeEnum;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.dto.RepositoryProviderCompareDiffDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.CHANGE_BRANCH_NAME_DUPLICATE;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.CHANGE_BRANCH_NAME_INVALID;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.CHANGE_CODE_REVIEWER_NOT_ASSIGNED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.CHANGE_CODE_REVIEWER_NOT_MATCH;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.CHANGE_LATEST_COMMIT_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.CHANGE_TESTER_NOT_ASSIGNED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.CHANGE_TESTER_NOT_MATCH;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.REPOSITORY_PROVIDER_GITLAB_BRANCH_CREATE_FAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChangeServiceImpl} 的单元测试。
 */
public class ChangeServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ChangeServiceImpl changeService;

    @Mock
    private ChangeMapper changeMapper;
    @Mock
    private ChangeEnvMapper changeEnvMapper;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private ApplicationEnvMapper applicationEnvMapper;
    @Mock
    private RepositoryProviderService repositoryProviderService;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache currentRunCache;

    @Test
    public void testMountChangeEnv_evictCurrentRunCache() throws Exception {
        // 调用
        Method method = ChangeServiceImpl.class.getMethod("mountChangeEnv", ChangeEnvMountReqVO.class, Long.class);
        CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);

        // 断言
        assertEquals(RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN, cacheEvict.value()[0]);
        assertEquals("#mountReqVO.applicationEnvId", cacheEvict.key());
    }

    @Test
    public void testUnmountChangeEnv_evictCurrentRunCache() throws Exception {
        // 调用
        Method method = ChangeServiceImpl.class.getMethod("unmountChangeEnv", ChangeEnvUnmountReqVO.class, Long.class);
        CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);

        // 断言
        assertEquals(RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN, cacheEvict.value()[0]);
        assertEquals("#unmountReqVO.applicationEnvId", cacheEvict.key());
    }

    @Test
    public void testCreateChangeFromApplication_success() {
        // 准备参数
        ChangeCreateFromApplicationReqVO reqVO = buildCreateFromApplicationReqVO("login-page");
        when(applicationMapper.selectById(eq(1L))).thenReturn(buildApplication());
        doAnswer(invocation -> {
            ChangeDO change = invocation.getArgument(0);
            change.setId(100L);
            return 1;
        }).when(changeMapper).insert(any(ChangeDO.class));

        // 调用
        Long id = changeService.createChangeFromApplication(reqVO, 7L);

        // 断言
        assertEquals(100L, id);
        ArgumentCaptor<ChangeDO> changeCaptor = ArgumentCaptor.forClass(ChangeDO.class);
        verify(changeMapper).insert(changeCaptor.capture());
        ChangeDO change = changeCaptor.getValue();
        assertEquals(1L, change.getAppId());
        assertEquals("gone-cloud-1717651234567", change.getChangeKey());
        assertEquals("登录页优化", change.getTitle());
        assertEquals("feat/login-page-1717651234567", change.getBranchName());
        assertEquals("master", change.getSourceBaseBranchName());
        assertEquals(7L, change.getOwnerUserId());
        assertEquals(ChangeStatusEnum.ACTIVE.getStatus(), change.getStatus());
        assertEquals(0, change.getTestPassed());
        assertEquals(ChangeCodeReviewStatusEnum.OPEN.getStatus(), change.getCodeReviewStatus());
        verify(repositoryProviderService).createRepositoryBranch(eq(10L), eq("group/gone-cloud"),
                eq("feat/login-page-1717651234567"), eq("master"));
    }

    @Test
    public void testSetTester_success() {
        // 准备参数
        ChangeSetTesterReqVO reqVO = new ChangeSetTesterReqVO();
        reqVO.setId(100L);
        reqVO.setTesterUserId(8L);
        ChangeDO change = new ChangeDO();
        change.setId(100L);
        change.setStatus(ChangeStatusEnum.ACTIVE.getStatus());
        when(changeMapper.selectById(eq(100L))).thenReturn(change);
        when(changeEnvMapper.selectListByChangeId(eq(100L))).thenReturn(List.of(
                buildChangeEnv(900L, 100L, 200L),
                buildChangeEnv(901L, 100L, 201L)));
        when(cacheManager.getCache(eq(RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN))).thenReturn(currentRunCache);

        // 调用
        changeService.setTester(reqVO);

        // 断言
        verify(changeMapper).updateTesterById(eq(100L), eq(8L), any(LocalDateTime.class));
        verify(currentRunCache).evict(eq(200L));
        verify(currentRunCache).evict(eq(201L));
    }

    @Test
    public void testSetTester_notActive() {
        // 准备参数
        ChangeSetTesterReqVO reqVO = new ChangeSetTesterReqVO();
        reqVO.setId(100L);
        reqVO.setTesterUserId(8L);
        ChangeDO change = new ChangeDO();
        change.setId(100L);
        change.setStatus(ChangeStatusEnum.RELEASED.getStatus());
        when(changeMapper.selectById(eq(100L))).thenReturn(change);

        // 调用并断言
        assertServiceException(() -> changeService.setTester(reqVO),
                cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.CHANGE_STATUS_NOT_ACTIVE);
        verify(changeMapper, never()).updateTesterById(any(), any(), any());
    }

    @Test
    public void testSetCodeReviewer_success() {
        // 准备参数
        ChangeSetCodeReviewerReqVO reqVO = new ChangeSetCodeReviewerReqVO();
        reqVO.setId(100L);
        reqVO.setCodeReviewerUserId(9L);
        ChangeDO change = new ChangeDO();
        change.setId(100L);
        change.setStatus(ChangeStatusEnum.ACTIVE.getStatus());
        when(changeMapper.selectById(eq(100L))).thenReturn(change);

        // 调用
        changeService.setCodeReviewer(reqVO);

        // 断言
        verify(changeMapper).updateCodeReviewerById(eq(100L), eq(9L), any(LocalDateTime.class));
    }

    @Test
    public void testSetCodeReviewer_notActive() {
        // 准备参数
        ChangeSetCodeReviewerReqVO reqVO = new ChangeSetCodeReviewerReqVO();
        reqVO.setId(100L);
        reqVO.setCodeReviewerUserId(9L);
        ChangeDO change = new ChangeDO();
        change.setId(100L);
        change.setStatus(ChangeStatusEnum.RELEASED.getStatus());
        when(changeMapper.selectById(eq(100L))).thenReturn(change);

        // 调用并断言
        assertServiceException(() -> changeService.setCodeReviewer(reqVO),
                cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.CHANGE_STATUS_NOT_ACTIVE);
        verify(changeMapper, never()).updateCodeReviewerById(any(), any(), any());
    }

    @Test
    public void testPassTest_success() {
        ChangeTestOperateReqVO reqVO = new ChangeTestOperateReqVO();
        reqVO.setId(100L);
        ChangeDO change = buildActiveChange();
        change.setTesterUserId(7L);
        change.setLatestCommitSha("sha-new");
        when(changeMapper.selectById(eq(100L))).thenReturn(change);

        changeService.passTest(reqVO, 7L);

        verify(changeMapper).updateTestPassedById(eq(100L), eq("sha-new"), any(LocalDateTime.class));
    }

    @Test
    public void testPassTest_latestCommitNotExists() {
        ChangeTestOperateReqVO reqVO = new ChangeTestOperateReqVO();
        reqVO.setId(100L);
        ChangeDO change = buildActiveChange();
        change.setTesterUserId(7L);
        when(changeMapper.selectById(eq(100L))).thenReturn(change);

        assertServiceException(() -> changeService.passTest(reqVO, 7L), CHANGE_LATEST_COMMIT_NOT_EXISTS);
        verify(changeMapper, never()).updateTestPassedById(any(), any(), any());
    }

    @Test
    public void testResetTest_success() {
        ChangeTestOperateReqVO reqVO = new ChangeTestOperateReqVO();
        reqVO.setId(100L);
        ChangeDO change = buildActiveChange();
        change.setTesterUserId(7L);
        when(changeMapper.selectById(eq(100L))).thenReturn(change);

        changeService.resetTest(reqVO, 7L);

        verify(changeMapper).updateTestResetById(eq(100L), any(LocalDateTime.class));
    }

    @Test
    public void testResetTest_notActive() {
        ChangeTestOperateReqVO reqVO = new ChangeTestOperateReqVO();
        reqVO.setId(100L);
        ChangeDO change = new ChangeDO();
        change.setId(100L);
        change.setStatus(ChangeStatusEnum.RELEASED.getStatus());
        when(changeMapper.selectById(eq(100L))).thenReturn(change);

        assertServiceException(() -> changeService.resetTest(reqVO, 7L),
                cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.CHANGE_STATUS_NOT_ACTIVE);
        verify(changeMapper, never()).updateTestResetById(any(), any());
    }

    @Test
    public void testPassTest_testerNotAssigned() {
        ChangeTestOperateReqVO reqVO = new ChangeTestOperateReqVO();
        reqVO.setId(100L);
        ChangeDO change = buildActiveChange();
        change.setLatestCommitSha("sha-new");
        when(changeMapper.selectById(eq(100L))).thenReturn(change);

        assertServiceException(() -> changeService.passTest(reqVO, 7L), CHANGE_TESTER_NOT_ASSIGNED);
        verify(changeMapper, never()).updateTestPassedById(any(), any(), any());
    }

    @Test
    public void testPassTest_testerNotMatch() {
        ChangeTestOperateReqVO reqVO = new ChangeTestOperateReqVO();
        reqVO.setId(100L);
        ChangeDO change = buildActiveChange();
        change.setTesterUserId(8L);
        change.setLatestCommitSha("sha-new");
        when(changeMapper.selectById(eq(100L))).thenReturn(change);

        assertServiceException(() -> changeService.passTest(reqVO, 7L), CHANGE_TESTER_NOT_MATCH);
        verify(changeMapper, never()).updateTestPassedById(any(), any(), any());
    }

    @Test
    public void testResetTest_testerNotAssigned() {
        ChangeTestOperateReqVO reqVO = new ChangeTestOperateReqVO();
        reqVO.setId(100L);
        ChangeDO change = buildActiveChange();
        when(changeMapper.selectById(eq(100L))).thenReturn(change);

        assertServiceException(() -> changeService.resetTest(reqVO, 7L), CHANGE_TESTER_NOT_ASSIGNED);
        verify(changeMapper, never()).updateTestResetById(any(), any());
    }

    @Test
    public void testResetTest_testerNotMatch() {
        ChangeTestOperateReqVO reqVO = new ChangeTestOperateReqVO();
        reqVO.setId(100L);
        ChangeDO change = buildActiveChange();
        change.setTesterUserId(8L);
        when(changeMapper.selectById(eq(100L))).thenReturn(change);

        assertServiceException(() -> changeService.resetTest(reqVO, 7L), CHANGE_TESTER_NOT_MATCH);
        verify(changeMapper, never()).updateTestResetById(any(), any());
    }

    @Test
    public void testGetCodeReviewDiff_useSourceBaseBranchWhenNeverApproved() {
        // 准备参数
        ChangeDO change = buildActiveChange();
        change.setLatestCommitSha("sha-new");
        when(changeMapper.selectById(eq(100L))).thenReturn(change);
        when(applicationMapper.selectById(eq(1L))).thenReturn(buildApplication());
        RepositoryProviderCompareDiffDTO diff = buildCompareDiff("src/App.java", false, false, false);
        when(repositoryProviderService.compareRepositoryDiff(eq(10L), eq("group/gone-cloud"),
                eq("master"), eq("sha-new"))).thenReturn(List.of(diff));

        // 调用
        ChangeCodeReviewDiffRespVO respVO = changeService.getCodeReviewDiff(100L);

        // 断言
        assertEquals(100L, respVO.getChangeId());
        assertEquals("master", respVO.getCompareBaseRef());
        assertEquals("sha-new", respVO.getCompareTargetRef());
        assertEquals(1, respVO.getFiles().size());
        assertEquals("src/App.java", respVO.getFiles().get(0).getPath());
        assertEquals("MODIFIED", respVO.getFiles().get(0).getChangeType());
    }

    @Test
    public void testGetCodeReviewDiff_useApprovedCommitWhenApprovedBefore() {
        // 准备参数
        ChangeDO change = buildActiveChange();
        change.setCodeReviewPassedCommitSha("sha-approved");
        change.setLatestCommitSha("sha-new");
        when(changeMapper.selectById(eq(100L))).thenReturn(change);
        when(applicationMapper.selectById(eq(1L))).thenReturn(buildApplication());
        RepositoryProviderCompareDiffDTO diff = buildCompareDiff("src/NewApp.java", false, false, true);
        when(repositoryProviderService.compareRepositoryDiff(eq(10L), eq("group/gone-cloud"),
                eq("sha-approved"), eq("sha-new"))).thenReturn(List.of(diff));

        // 调用
        ChangeCodeReviewDiffRespVO respVO = changeService.getCodeReviewDiff(100L);

        // 断言
        assertEquals("sha-approved", respVO.getCompareBaseRef());
        assertEquals("sha-new", respVO.getCompareTargetRef());
        assertEquals("RENAMED", respVO.getFiles().get(0).getChangeType());
    }

    @Test
    public void testStartCodeReview_success() {
        // 准备参数
        ChangeCodeReviewOperateReqVO reqVO = new ChangeCodeReviewOperateReqVO();
        reqVO.setId(100L);
        ChangeDO change = buildActiveChange();
        change.setCodeReviewerUserId(null);
        change.setCodeReviewStatus(ChangeCodeReviewStatusEnum.OPEN.getStatus());
        when(changeMapper.selectById(eq(100L))).thenReturn(change);

        // 调用
        changeService.startCodeReview(reqVO, 9L);

        // 断言
        verify(changeMapper).updateCodeReviewInProgressById(eq(100L), eq(9L), any(LocalDateTime.class));
    }

    @Test
    public void testStartCodeReview_approvedDoesNotDowngrade() {
        // 准备参数
        ChangeCodeReviewOperateReqVO reqVO = new ChangeCodeReviewOperateReqVO();
        reqVO.setId(100L);
        ChangeDO change = buildActiveChange();
        change.setCodeReviewStatus(ChangeCodeReviewStatusEnum.APPROVED.getStatus());
        when(changeMapper.selectById(eq(100L))).thenReturn(change);

        // 调用
        changeService.startCodeReview(reqVO, 9L);

        // 断言
        verify(changeMapper, never()).updateCodeReviewInProgressById(any(), any(), any());
    }

    @Test
    public void testApproveCodeReview_success() {
        // 准备参数
        ChangeCodeReviewOperateReqVO reqVO = new ChangeCodeReviewOperateReqVO();
        reqVO.setId(100L);
        ChangeDO change = buildActiveChange();
        change.setCodeReviewerUserId(9L);
        change.setLatestCommitSha("sha-new");
        when(changeMapper.selectById(eq(100L))).thenReturn(change);

        // 调用
        changeService.approveCodeReview(reqVO, 9L);

        // 断言
        verify(changeMapper).updateCodeReviewApprovedById(eq(100L), eq(9L), eq("sha-new"),
                any(LocalDateTime.class));
    }

    @Test
    public void testApproveCodeReview_latestCommitNotExists() {
        // 准备参数
        ChangeCodeReviewOperateReqVO reqVO = new ChangeCodeReviewOperateReqVO();
        reqVO.setId(100L);
        ChangeDO change = buildActiveChange();
        when(changeMapper.selectById(eq(100L))).thenReturn(change);

        // 调用并断言
        assertServiceException(() -> changeService.approveCodeReview(reqVO, 9L), CHANGE_LATEST_COMMIT_NOT_EXISTS);
        verify(changeMapper, never()).updateCodeReviewApprovedById(any(), any(), any(), any());
    }

    @Test
    public void testApproveCodeReview_codeReviewerNotAssigned() {
        ChangeCodeReviewOperateReqVO reqVO = new ChangeCodeReviewOperateReqVO();
        reqVO.setId(100L);
        ChangeDO change = buildActiveChange();
        change.setLatestCommitSha("sha-new");
        when(changeMapper.selectById(eq(100L))).thenReturn(change);

        assertServiceException(() -> changeService.approveCodeReview(reqVO, 9L), CHANGE_CODE_REVIEWER_NOT_ASSIGNED);
        verify(changeMapper, never()).updateCodeReviewApprovedById(any(), any(), any(), any());
    }

    @Test
    public void testApproveCodeReview_codeReviewerNotMatch() {
        ChangeCodeReviewOperateReqVO reqVO = new ChangeCodeReviewOperateReqVO();
        reqVO.setId(100L);
        ChangeDO change = buildActiveChange();
        change.setCodeReviewerUserId(8L);
        change.setLatestCommitSha("sha-new");
        when(changeMapper.selectById(eq(100L))).thenReturn(change);

        assertServiceException(() -> changeService.approveCodeReview(reqVO, 9L), CHANGE_CODE_REVIEWER_NOT_MATCH);
        verify(changeMapper, never()).updateCodeReviewApprovedById(any(), any(), any(), any());
    }

    @Test
    public void testCreateChangeFromApplication_invalidBranchName() {
        // 准备参数
        ChangeCreateFromApplicationReqVO reqVO = buildCreateFromApplicationReqVO("登录页");
        when(applicationMapper.selectById(eq(1L))).thenReturn(buildApplication());

        // 调用并断言
        assertServiceException(() -> changeService.createChangeFromApplication(reqVO, 7L), CHANGE_BRANCH_NAME_INVALID);
        verify(repositoryProviderService, never()).createRepositoryBranch(any(), any(), any(), any());
        verify(changeMapper, never()).insert(any(ChangeDO.class));
    }

    @Test
    public void testCreateChangeFromApplication_duplicateBranchName() {
        // 准备参数
        ChangeCreateFromApplicationReqVO reqVO = buildCreateFromApplicationReqVO("login-page");
        when(applicationMapper.selectById(eq(1L))).thenReturn(buildApplication());
        ChangeDO existChange = new ChangeDO();
        existChange.setId(99L);
        when(changeMapper.selectByAppIdAndBranchName(eq(1L), eq("feat/login-page-1717651234567")))
                .thenReturn(existChange);

        // 调用并断言
        assertServiceException(() -> changeService.createChangeFromApplication(reqVO, 7L), CHANGE_BRANCH_NAME_DUPLICATE);
        verify(repositoryProviderService, never()).createRepositoryBranch(any(), any(), any(), any());
        verify(changeMapper, never()).insert(any(ChangeDO.class));
    }

    @Test
    public void testCreateChangeFromApplication_createRepositoryBranchFail() {
        // 准备参数
        ChangeCreateFromApplicationReqVO reqVO = buildCreateFromApplicationReqVO("login-page");
        when(applicationMapper.selectById(eq(1L))).thenReturn(buildApplication());
        doThrow(exception(REPOSITORY_PROVIDER_GITLAB_BRANCH_CREATE_FAIL, "branch exists"))
                .when(repositoryProviderService).createRepositoryBranch(eq(10L), eq("group/gone-cloud"),
                        eq("feat/login-page-1717651234567"), eq("master"));

        // 调用并断言
        assertServiceException(() -> changeService.createChangeFromApplication(reqVO, 7L),
                REPOSITORY_PROVIDER_GITLAB_BRANCH_CREATE_FAIL, "branch exists");
        verify(changeMapper, never()).insert(any(ChangeDO.class));
    }

    @Test
    public void testSyncLatestCommitFromGitLabPushHook_success() {
        // 准备参数
        RepositoryProviderGitLabPushHookReqVO reqVO = buildGitLabPushHookReqVO();
        when(repositoryProviderService.validateRepositoryProviderExists(eq(10L))).thenReturn(buildRepositoryProvider());
        when(applicationMapper.selectByRepositoryProviderIdAndRepoIdentifier(eq(10L), eq("group/gone-cloud")))
                .thenReturn(buildApplication());
        ChangeDO change = new ChangeDO();
        change.setId(100L);
        change.setLatestCommitSha("sha-old");
        change.setTesterUserId(8L);
        change.setTestPassed(1);
        change.setTestPassedCommitSha("sha-old");
        change.setCodeReviewerUserId(9L);
        change.setCodeReviewStatus(ChangeCodeReviewStatusEnum.APPROVED.getStatus());
        change.setCodeReviewPassedCommitSha("sha-old");
        when(changeMapper.selectByAppIdAndBranchNameAndStatus(eq(1L),
                eq("feat/login-page-1717651234567"), eq(ChangeStatusEnum.ACTIVE.getStatus()))).thenReturn(change);

        // 调用
        boolean handled = changeService.syncLatestCommitFromGitLabPushHook(10L, reqVO);

        // 断言
        assertTrue(handled);
        verify(changeMapper).updateLatestCommitAndResetReviewTest(eq(100L), eq("sha-new"), eq("更新登录页"),
                eq(LocalDateTime.of(2026, 6, 7, 12, 30)), any(LocalDateTime.class));
        verify(changeMapper, never()).updateById(any(ChangeDO.class));
    }

    @Test
    public void testSyncLatestCommitFromGitLabPushHook_sameCommitDoesNotResetReviewTest() {
        // 准备参数
        RepositoryProviderGitLabPushHookReqVO reqVO = buildGitLabPushHookReqVO();
        when(repositoryProviderService.validateRepositoryProviderExists(eq(10L))).thenReturn(buildRepositoryProvider());
        when(applicationMapper.selectByRepositoryProviderIdAndRepoIdentifier(eq(10L), eq("group/gone-cloud")))
                .thenReturn(buildApplication());
        ChangeDO change = new ChangeDO();
        change.setId(100L);
        change.setLatestCommitSha("sha-new");
        when(changeMapper.selectByAppIdAndBranchNameAndStatus(eq(1L),
                eq("feat/login-page-1717651234567"), eq(ChangeStatusEnum.ACTIVE.getStatus()))).thenReturn(change);

        // 调用
        boolean handled = changeService.syncLatestCommitFromGitLabPushHook(10L, reqVO);

        // 断言
        assertTrue(handled);
        ArgumentCaptor<ChangeDO> changeCaptor = ArgumentCaptor.forClass(ChangeDO.class);
        verify(changeMapper).updateById(changeCaptor.capture());
        ChangeDO updateObj = changeCaptor.getValue();
        assertEquals(100L, updateObj.getId());
        assertEquals("sha-new", updateObj.getLatestCommitSha());
        assertEquals("更新登录页", updateObj.getLatestCommitMessage());
        assertEquals(LocalDateTime.of(2026, 6, 7, 12, 30), updateObj.getLatestCommitAt());
        verify(changeMapper, never()).updateLatestCommitAndResetReviewTest(any(), any(), any(), any(), any());
    }

    @Test
    public void testSyncLatestCommitFromGitLabPushHook_deleteBranchIgnored() {
        // 准备参数
        RepositoryProviderGitLabPushHookReqVO reqVO = buildGitLabPushHookReqVO();
        reqVO.setAfter("0000000000000000000000000000000000000000");
        reqVO.setCheckoutSha(null);

        // 调用
        boolean handled = changeService.syncLatestCommitFromGitLabPushHook(10L, reqVO);

        // 断言
        assertFalse(handled);
        verify(repositoryProviderService, never()).validateRepositoryProviderExists(any());
        verify(changeMapper, never()).updateById(any(ChangeDO.class));
    }

    @Test
    public void testSyncLatestCommitFromGitLabPushHook_changeNotMatched() {
        // 准备参数
        RepositoryProviderGitLabPushHookReqVO reqVO = buildGitLabPushHookReqVO();
        when(repositoryProviderService.validateRepositoryProviderExists(eq(10L))).thenReturn(buildRepositoryProvider());
        when(applicationMapper.selectByRepositoryProviderIdAndRepoIdentifier(eq(10L), eq("group/gone-cloud")))
                .thenReturn(buildApplication());
        when(changeMapper.selectByAppIdAndBranchNameAndStatus(eq(1L),
                eq("feat/login-page-1717651234567"), eq(ChangeStatusEnum.ACTIVE.getStatus()))).thenReturn(null);

        // 调用
        boolean handled = changeService.syncLatestCommitFromGitLabPushHook(10L, reqVO);

        // 断言
        assertFalse(handled);
        verify(changeMapper, never()).updateById(any(ChangeDO.class));
    }

    private ChangeCreateFromApplicationReqVO buildCreateFromApplicationReqVO(String branchSlug) {
        ChangeCreateFromApplicationReqVO reqVO = new ChangeCreateFromApplicationReqVO();
        reqVO.setAppId(1L);
        reqVO.setTitle("登录页优化");
        reqVO.setBranchSlug(branchSlug);
        reqVO.setOpenTimestamp(1717651234567L);
        return reqVO;
    }

    private ApplicationDO buildApplication() {
        ApplicationDO application = new ApplicationDO();
        application.setId(1L);
        application.setAppKey("gone-cloud");
        application.setRepositoryProviderId(10L);
        application.setRepoIdentifier("group/gone-cloud");
        application.setDefaultBranchName("master");
        return application;
    }

    private ChangeDO buildActiveChange() {
        ChangeDO change = new ChangeDO();
        change.setId(100L);
        change.setAppId(1L);
        change.setBranchName("feat/login-page-1717651234567");
        change.setSourceBaseBranchName("master");
        change.setStatus(ChangeStatusEnum.ACTIVE.getStatus());
        change.setCodeReviewStatus(ChangeCodeReviewStatusEnum.OPEN.getStatus());
        return change;
    }

    private ChangeEnvDO buildChangeEnv(Long id, Long changeId, Long applicationEnvId) {
        ChangeEnvDO changeEnv = new ChangeEnvDO();
        changeEnv.setId(id);
        changeEnv.setChangeId(changeId);
        changeEnv.setApplicationEnvId(applicationEnvId);
        return changeEnv;
    }

    private RepositoryProviderCompareDiffDTO buildCompareDiff(String path, boolean newFile,
                                                             boolean deletedFile, boolean renamedFile) {
        RepositoryProviderCompareDiffDTO diff = new RepositoryProviderCompareDiffDTO();
        diff.setOldPath(renamedFile ? "src/OldApp.java" : path);
        diff.setNewPath(path);
        diff.setNewFile(newFile);
        diff.setDeletedFile(deletedFile);
        diff.setRenamedFile(renamedFile);
        diff.setDiff("@@ -1 +1 @@");
        return diff;
    }

    private RepositoryProviderDO buildRepositoryProvider() {
        RepositoryProviderDO repositoryProvider = new RepositoryProviderDO();
        repositoryProvider.setId(10L);
        repositoryProvider.setProviderType(RepositoryProviderTypeEnum.GITLAB.getProviderType());
        return repositoryProvider;
    }

    private RepositoryProviderGitLabPushHookReqVO buildGitLabPushHookReqVO() {
        RepositoryProviderGitLabPushHookReqVO reqVO = new RepositoryProviderGitLabPushHookReqVO();
        reqVO.setObjectKind("push");
        reqVO.setRef("refs/heads/feat/login-page-1717651234567");
        reqVO.setAfter("sha-new");
        reqVO.setCheckoutSha("sha-new");
        RepositoryProviderGitLabPushHookReqVO.Project project = new RepositoryProviderGitLabPushHookReqVO.Project();
        project.setPathWithNamespace("group/gone-cloud");
        reqVO.setProject(project);
        RepositoryProviderGitLabPushHookReqVO.Commit oldCommit = new RepositoryProviderGitLabPushHookReqVO.Commit();
        oldCommit.setId("sha-old");
        oldCommit.setMessage("旧提交");
        oldCommit.setTimestamp("2026-06-07T12:00:00+08:00");
        RepositoryProviderGitLabPushHookReqVO.Commit newCommit = new RepositoryProviderGitLabPushHookReqVO.Commit();
        newCommit.setId("sha-new");
        newCommit.setMessage("更新登录页");
        newCommit.setTimestamp("2026-06-07T12:30:00+08:00");
        reqVO.setCommits(List.of(oldCommit, newCommit));
        return reqVO;
    }

}
