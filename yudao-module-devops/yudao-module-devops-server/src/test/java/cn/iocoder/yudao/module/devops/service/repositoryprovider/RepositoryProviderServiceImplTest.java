package cn.iocoder.yudao.module.devops.service.repositoryprovider;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.repositoryprovider.RepositoryProviderMapper;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderAuthTypeEnum;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderTypeEnum;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.dto.RepositoryProviderCompareDiffDTO;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.RepositoryApi;
import org.gitlab4j.api.models.CompareResults;
import org.gitlab4j.api.models.Diff;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.REPOSITORY_PROVIDER_GITLAB_BRANCH_CREATE_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.REPOSITORY_PROVIDER_GITLAB_BRANCH_DELETE_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.REPOSITORY_PROVIDER_GITLAB_COMPARE_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.REPOSITORY_PROVIDER_DELETE_FAIL_APPLICATION_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.REPOSITORY_PROVIDER_TYPE_NOT_SUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
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
    @Mock
    private GitLabApi gitLabApi;
    @Mock
    private RepositoryApi repositoryApi;

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

    @Test
    public void testCreateRepositoryBranch_success() throws Exception {
        // 准备参数
        RepositoryProviderDO repositoryProvider = buildGitLabRepositoryProvider();
        when(repositoryProviderMapper.selectById(eq(10L))).thenReturn(repositoryProvider);
        when(gitLabApi.getRepositoryApi()).thenReturn(repositoryApi);
        RepositoryProviderServiceImpl service = spy(repositoryProviderService);
        doReturn(gitLabApi).when(service).createGitLabApi(any(RepositoryProviderDO.class));

        // 调用
        service.createRepositoryBranch(10L, "group/gone-cloud", "feat/login-page-1717651234567", "master");

        // 断言
        verify(repositoryApi).createBranch(eq("group/gone-cloud"), eq("feat/login-page-1717651234567"), eq("master"));
    }

    @Test
    public void testCreateRepositoryBranch_gitLabFail() throws Exception {
        // 准备参数
        RepositoryProviderDO repositoryProvider = buildGitLabRepositoryProvider();
        when(repositoryProviderMapper.selectById(eq(10L))).thenReturn(repositoryProvider);
        when(gitLabApi.getRepositoryApi()).thenReturn(repositoryApi);
        doThrow(new GitLabApiException("branch exists", 400)).when(repositoryApi)
                .createBranch(eq("group/gone-cloud"), eq("feat/login-page-1717651234567"), eq("master"));
        RepositoryProviderServiceImpl service = spy(repositoryProviderService);
        doReturn(gitLabApi).when(service).createGitLabApi(any(RepositoryProviderDO.class));

        // 调用并断言
        assertServiceException(() -> service.createRepositoryBranch(10L, "group/gone-cloud",
                "feat/login-page-1717651234567", "master"), REPOSITORY_PROVIDER_GITLAB_BRANCH_CREATE_FAIL, "branch exists");
    }

    @Test
    public void testCreateRepositoryBranch_providerTypeNotSupported() {
        // 准备参数
        RepositoryProviderDO repositoryProvider = buildGitLabRepositoryProvider();
        repositoryProvider.setProviderType("GITHUB");
        when(repositoryProviderMapper.selectById(eq(10L))).thenReturn(repositoryProvider);

        // 调用并断言
        assertServiceException(() -> repositoryProviderService.createRepositoryBranch(10L, "group/gone-cloud",
                "feat/login-page-1717651234567", "master"), REPOSITORY_PROVIDER_TYPE_NOT_SUPPORTED);
    }

    @Test
    public void testCompareRepositoryDiff_success() throws Exception {
        // 准备参数
        RepositoryProviderDO repositoryProvider = buildGitLabRepositoryProvider();
        when(repositoryProviderMapper.selectById(eq(10L))).thenReturn(repositoryProvider);
        when(gitLabApi.getRepositoryApi()).thenReturn(repositoryApi);
        Diff diff = new Diff();
        diff.setOldPath("src/OldApp.java");
        diff.setNewPath("src/App.java");
        diff.setNewFile(false);
        diff.setDeletedFile(false);
        diff.setRenamedFile(true);
        diff.setDiff("@@ -1 +1 @@");
        CompareResults compareResults = new CompareResults();
        compareResults.setDiffs(List.of(diff));
        when(repositoryApi.compare(eq("group/gone-cloud"), eq("sha-approved"), eq("sha-new")))
                .thenReturn(compareResults);
        RepositoryProviderServiceImpl service = spy(repositoryProviderService);
        doReturn(gitLabApi).when(service).createGitLabApi(any(RepositoryProviderDO.class));

        // 调用
        List<RepositoryProviderCompareDiffDTO> diffList = service.compareRepositoryDiff(10L, "group/gone-cloud",
                "sha-approved", "sha-new");

        // 断言
        assertEquals(1, diffList.size());
        assertEquals("src/OldApp.java", diffList.get(0).getOldPath());
        assertEquals("src/App.java", diffList.get(0).getNewPath());
        assertEquals(true, diffList.get(0).getRenamedFile());
        assertEquals("@@ -1 +1 @@", diffList.get(0).getDiff());
    }

    @Test
    public void testCompareRepositoryDiff_gitLabFail() throws Exception {
        // 准备参数
        RepositoryProviderDO repositoryProvider = buildGitLabRepositoryProvider();
        when(repositoryProviderMapper.selectById(eq(10L))).thenReturn(repositoryProvider);
        when(gitLabApi.getRepositoryApi()).thenReturn(repositoryApi);
        doThrow(new GitLabApiException("compare fail", 400)).when(repositoryApi)
                .compare(eq("group/gone-cloud"), eq("sha-approved"), eq("sha-new"));
        RepositoryProviderServiceImpl service = spy(repositoryProviderService);
        doReturn(gitLabApi).when(service).createGitLabApi(any(RepositoryProviderDO.class));

        // 调用并断言
        assertServiceException(() -> service.compareRepositoryDiff(10L, "group/gone-cloud",
                "sha-approved", "sha-new"), REPOSITORY_PROVIDER_GITLAB_COMPARE_FAIL, "compare fail");
    }

    @Test
    public void testDeleteRepositoryBranch_success() throws Exception {
        RepositoryProviderDO repositoryProvider = buildGitLabRepositoryProvider();
        when(repositoryProviderMapper.selectById(eq(10L))).thenReturn(repositoryProvider);
        when(gitLabApi.getRepositoryApi()).thenReturn(repositoryApi);
        RepositoryProviderServiceImpl service = spy(repositoryProviderService);
        doReturn(gitLabApi).when(service).createGitLabApi(any(RepositoryProviderDO.class));

        service.deleteRepositoryBranch(10L, "group/gone-cloud", "feat/login-page-1717651234567");

        verify(repositoryApi).deleteBranch(eq("group/gone-cloud"), eq("feat/login-page-1717651234567"));
    }

    @Test
    public void testDeleteRepositoryBranch_notFoundIgnored() throws Exception {
        RepositoryProviderDO repositoryProvider = buildGitLabRepositoryProvider();
        when(repositoryProviderMapper.selectById(eq(10L))).thenReturn(repositoryProvider);
        when(gitLabApi.getRepositoryApi()).thenReturn(repositoryApi);
        doThrow(new GitLabApiException("not found", 404)).when(repositoryApi)
                .deleteBranch(eq("group/gone-cloud"), eq("feat/login-page-1717651234567"));
        RepositoryProviderServiceImpl service = spy(repositoryProviderService);
        doReturn(gitLabApi).when(service).createGitLabApi(any(RepositoryProviderDO.class));

        service.deleteRepositoryBranch(10L, "group/gone-cloud", "feat/login-page-1717651234567");
    }

    @Test
    public void testDeleteRepositoryBranch_gitLabFail() throws Exception {
        RepositoryProviderDO repositoryProvider = buildGitLabRepositoryProvider();
        when(repositoryProviderMapper.selectById(eq(10L))).thenReturn(repositoryProvider);
        when(gitLabApi.getRepositoryApi()).thenReturn(repositoryApi);
        doThrow(new GitLabApiException("delete fail", 500)).when(repositoryApi)
                .deleteBranch(eq("group/gone-cloud"), eq("feat/login-page-1717651234567"));
        RepositoryProviderServiceImpl service = spy(repositoryProviderService);
        doReturn(gitLabApi).when(service).createGitLabApi(any(RepositoryProviderDO.class));

        assertServiceException(() -> service.deleteRepositoryBranch(10L, "group/gone-cloud",
                "feat/login-page-1717651234567"), REPOSITORY_PROVIDER_GITLAB_BRANCH_DELETE_FAIL, "delete fail");
    }

    private RepositoryProviderDO buildGitLabRepositoryProvider() {
        RepositoryProviderDO repositoryProvider = new RepositoryProviderDO();
        repositoryProvider.setId(10L);
        repositoryProvider.setProviderType(RepositoryProviderTypeEnum.GITLAB.getProviderType());
        repositoryProvider.setAuthType(RepositoryProviderAuthTypeEnum.ACCESS_TOKEN.getAuthType());
        repositoryProvider.setServerUrl("https://gitlab.example.com");
        repositoryProvider.setAccessToken("glpat-token");
        return repositoryProvider;
    }

}
