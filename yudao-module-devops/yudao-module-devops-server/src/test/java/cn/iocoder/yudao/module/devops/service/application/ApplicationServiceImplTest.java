package cn.iocoder.yudao.module.devops.service.application;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.environment.EnvironmentMapper;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderTypeEnum;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_REPO_IDENTIFIER_DUPLICATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationServiceImpl} 的单元测试。
 */
public class ApplicationServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ApplicationServiceImpl applicationService;

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private ApplicationEnvMapper applicationEnvMapper;
    @Mock
    private EnvironmentMapper environmentMapper;
    @Mock
    private ChangeMapper changeMapper;
    @Mock
    private RepositoryProviderService repositoryProviderService;

    @Test
    public void testCreateApplication_repositoryProviderLinkage() {
        // 准备参数
        ApplicationSaveReqVO reqVO = buildSaveReqVO(10L, "group/gone-cloud");
        reqVO.setRepoProviderType("GITHUB");
        RepositoryProviderDO repositoryProvider = buildRepositoryProvider(10L,
                RepositoryProviderTypeEnum.GITLAB.getProviderType());
        when(repositoryProviderService.validateRepositoryProviderExists(eq(10L))).thenReturn(repositoryProvider);
        doAnswer(invocation -> {
            ApplicationDO application = invocation.getArgument(0);
            application.setId(100L);
            return 1;
        }).when(applicationMapper).insert(any(ApplicationDO.class));

        // 调用
        Long id = applicationService.createApplication(reqVO);

        // 断言
        assertEquals(100L, id);
        ArgumentCaptor<ApplicationDO> applicationCaptor = ArgumentCaptor.forClass(ApplicationDO.class);
        verify(applicationMapper).insert(applicationCaptor.capture());
        ApplicationDO application = applicationCaptor.getValue();
        assertEquals(10L, application.getRepositoryProviderId());
        assertEquals(RepositoryProviderTypeEnum.GITLAB.getProviderType(), application.getRepoProviderType());
        assertEquals("group/gone-cloud", application.getRepoIdentifier());
        verify(applicationMapper).selectByRepositoryProviderIdAndRepoIdentifier(eq(10L), eq("group/gone-cloud"));
    }

    @Test
    public void testCreateApplication_duplicateRepositoryInSameProvider() {
        // 准备参数
        ApplicationSaveReqVO reqVO = buildSaveReqVO(10L, "group/gone-cloud");
        when(repositoryProviderService.validateRepositoryProviderExists(eq(10L)))
                .thenReturn(buildRepositoryProvider(10L, RepositoryProviderTypeEnum.GITLAB.getProviderType()));
        ApplicationDO existApplication = new ApplicationDO();
        existApplication.setId(99L);
        when(applicationMapper.selectByRepositoryProviderIdAndRepoIdentifier(eq(10L), eq("group/gone-cloud")))
                .thenReturn(existApplication);

        // 调用并断言
        assertServiceException(() -> applicationService.createApplication(reqVO), APPLICATION_REPO_IDENTIFIER_DUPLICATE);
    }

    @Test
    public void testUpdateApplication_repositoryProviderLinkage() {
        // 准备参数
        ApplicationSaveReqVO reqVO = buildSaveReqVO(20L, "group/gone-cloud");
        reqVO.setId(100L);
        reqVO.setRepoProviderType("GITHUB");
        ApplicationDO oldApplication = new ApplicationDO();
        oldApplication.setId(100L);
        when(applicationMapper.selectById(eq(100L))).thenReturn(oldApplication);
        when(repositoryProviderService.validateRepositoryProviderExists(eq(20L)))
                .thenReturn(buildRepositoryProvider(20L, RepositoryProviderTypeEnum.GITLAB.getProviderType()));

        // 调用
        applicationService.updateApplication(reqVO);

        // 断言
        ArgumentCaptor<ApplicationDO> applicationCaptor = ArgumentCaptor.forClass(ApplicationDO.class);
        verify(applicationMapper).updateById(applicationCaptor.capture());
        ApplicationDO application = applicationCaptor.getValue();
        assertEquals(100L, application.getId());
        assertEquals(20L, application.getRepositoryProviderId());
        assertEquals(RepositoryProviderTypeEnum.GITLAB.getProviderType(), application.getRepoProviderType());
    }

    private ApplicationSaveReqVO buildSaveReqVO(Long repositoryProviderId, String repoIdentifier) {
        ApplicationSaveReqVO reqVO = new ApplicationSaveReqVO();
        reqVO.setAppKey("gone-cloud");
        reqVO.setName("Gone Cloud");
        reqVO.setRepositoryProviderId(repositoryProviderId);
        reqVO.setRepoIdentifier(repoIdentifier);
        reqVO.setRepoUrl("https://gitlab.example.com/" + repoIdentifier);
        reqVO.setDefaultBranchName("master");
        reqVO.setOwnerUserId(1L);
        reqVO.setStatus(0);
        return reqVO;
    }

    private RepositoryProviderDO buildRepositoryProvider(Long id, String providerType) {
        RepositoryProviderDO repositoryProvider = new RepositoryProviderDO();
        repositoryProvider.setId(id);
        repositoryProvider.setProviderType(providerType);
        return repositoryProvider;
    }

}
