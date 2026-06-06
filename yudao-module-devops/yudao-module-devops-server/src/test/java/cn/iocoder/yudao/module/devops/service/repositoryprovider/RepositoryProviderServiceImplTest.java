package cn.iocoder.yudao.module.devops.service.repositoryprovider;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.repositoryprovider.RepositoryProviderMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.REPOSITORY_PROVIDER_DELETE_FAIL_APPLICATION_EXISTS;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RepositoryProviderServiceImpl} 的单元测试。
 */
public class RepositoryProviderServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private RepositoryProviderServiceImpl repositoryProviderService;

    @Mock
    private RepositoryProviderMapper repositoryProviderMapper;
    @Mock
    private ApplicationMapper applicationMapper;

    @Test
    public void testDeleteRepositoryProvider_applicationExists() {
        // 准备参数
        RepositoryProviderDO repositoryProvider = new RepositoryProviderDO();
        repositoryProvider.setId(10L);
        when(repositoryProviderMapper.selectById(eq(10L))).thenReturn(repositoryProvider);
        when(applicationMapper.selectCountByRepositoryProviderId(eq(10L))).thenReturn(1L);

        // 调用并断言
        assertServiceException(() -> repositoryProviderService.deleteRepositoryProvider(10L),
                REPOSITORY_PROVIDER_DELETE_FAIL_APPLICATION_EXISTS);
    }

    @Test
    public void testDeleteRepositoryProvider_success() {
        // 准备参数
        RepositoryProviderDO repositoryProvider = new RepositoryProviderDO();
        repositoryProvider.setId(10L);
        when(repositoryProviderMapper.selectById(eq(10L))).thenReturn(repositoryProvider);
        when(applicationMapper.selectCountByRepositoryProviderId(eq(10L))).thenReturn(0L);

        // 调用
        repositoryProviderService.deleteRepositoryProvider(10L);

        // 断言
        verify(repositoryProviderMapper).deleteById(eq(10L));
    }

}
