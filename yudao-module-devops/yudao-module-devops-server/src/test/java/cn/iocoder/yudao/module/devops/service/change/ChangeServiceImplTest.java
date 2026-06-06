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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.CHANGE_BRANCH_NAME_DUPLICATE;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.CHANGE_BRANCH_NAME_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
    }

    @Test
    public void testCreateChangeFromApplication_invalidBranchName() {
        // 准备参数
        ChangeCreateFromApplicationReqVO reqVO = buildCreateFromApplicationReqVO("登录页");
        when(applicationMapper.selectById(eq(1L))).thenReturn(buildApplication());

        // 调用并断言
        assertServiceException(() -> changeService.createChangeFromApplication(reqVO, 7L), CHANGE_BRANCH_NAME_INVALID);
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
        application.setDefaultBranchName("master");
        return application;
    }

}
