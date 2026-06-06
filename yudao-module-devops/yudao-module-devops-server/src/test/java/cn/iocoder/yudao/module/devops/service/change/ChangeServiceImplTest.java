package cn.iocoder.yudao.module.devops.service.change;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeCreateFromApplicationReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeMapper;
import cn.iocoder.yudao.module.devops.enums.ChangeStatusEnum;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.CHANGE_BRANCH_NAME_DUPLICATE;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.CHANGE_BRANCH_NAME_INVALID;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.REPOSITORY_PROVIDER_GITLAB_BRANCH_CREATE_FAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        verify(repositoryProviderService).createRepositoryBranch(eq(10L), eq("group/gone-cloud"),
                eq("feat/login-page-1717651234567"), eq("master"));
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

}
