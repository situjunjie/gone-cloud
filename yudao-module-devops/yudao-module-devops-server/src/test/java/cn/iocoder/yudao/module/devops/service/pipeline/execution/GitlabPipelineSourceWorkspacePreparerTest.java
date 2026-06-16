package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderTypeEnum;
import cn.iocoder.yudao.module.devops.framework.git.GitCommandExecutor;
import cn.iocoder.yudao.module.devops.framework.git.GitCommandResult;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GitlabPipelineSourceWorkspacePreparer} 单元测试。
 */
class GitlabPipelineSourceWorkspacePreparerTest {

    @InjectMocks
    private GitlabPipelineSourceWorkspacePreparer preparer;

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private RepositoryProviderService repositoryProviderService;
    @Mock
    private GitCommandExecutor gitCommandExecutor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testPrepare_noSourcesWithApplication_cloneApplicationDefaultBranch() {
        PipelineSpec spec = new PipelineSpec();
        Path workspace = Path.of("/tmp/workspace");
        ApplicationDO application = application();
        RepositoryProviderDO provider = provider();
        when(applicationMapper.selectById(10L)).thenReturn(application);
        when(repositoryProviderService.getRepositoryProvider(20L)).thenReturn(provider);
        when(gitCommandExecutor.execute(any(), any(), eq(true))).thenReturn(new GitCommandResult(0, ""));

        preparer.prepare(run(), spec, workspace, Map.of());

        verify(gitCommandExecutor).execute(eq(workspace),
                eq(List.of("git", "clone", "--depth", "1", "--branch", "master",
                        "https://oauth2:token%2Fvalue@gitlab.example.com/group/repo.git", ".")),
                eq(true));
    }

    @Test
    void testPrepare_noSourcesWithoutApplication_noGitClone() {
        PipelineSpec spec = new PipelineSpec();
        PipelineRunDO run = new PipelineRunDO();
        run.setId(1L);

        preparer.prepare(run, spec, Path.of("/tmp/workspace"), Map.of());

        verify(gitCommandExecutor, never()).execute(any(), any(), eq(true));
    }

    @Test
    void testPrepare_gitlabSource_cloneApplicationDefaultBranch() {
        PipelineSpec spec = specWithGitlabSource();
        Path workspace = Path.of("/tmp/workspace");
        ApplicationDO application = application();
        RepositoryProviderDO provider = provider();
        when(applicationMapper.selectById(10L)).thenReturn(application);
        when(repositoryProviderService.getRepositoryProvider(20L)).thenReturn(provider);
        when(gitCommandExecutor.execute(any(), any(), eq(true))).thenReturn(new GitCommandResult(0, ""));

        preparer.prepare(run(), spec, workspace, Map.of());

        verify(gitCommandExecutor).execute(eq(workspace),
                eq(List.of("git", "clone", "--depth", "1", "--branch", "master",
                        "https://oauth2:token%2Fvalue@gitlab.example.com/group/repo.git", ".")),
                eq(true));
    }

    @Test
    void testPrepare_gitlabSourceWithoutApplication_cloneYamlSource() {
        PipelineSpec spec = specWithGitlabSource();
        Path workspace = Path.of("/tmp/workspace");
        PipelineRunDO run = new PipelineRunDO();
        run.setId(1L);
        when(gitCommandExecutor.execute(any(), any(), eq(true))).thenReturn(new GitCommandResult(0, ""));

        preparer.prepare(run, spec, workspace, Map.of());

        verify(gitCommandExecutor).execute(eq(workspace),
                eq(List.of("git", "clone", "--depth", "1", "--branch", "main",
                        "https://example.com/group/repo.git", ".")),
                eq(true));
    }

    @Test
    void testPrepare_gitlabSource_cloneMergedBranchAndCheckoutCommit() {
        PipelineSpec spec = specWithGitlabSource();
        Path workspace = Path.of("/tmp/workspace");
        ApplicationDO application = application();
        RepositoryProviderDO provider = provider();
        when(applicationMapper.selectById(10L)).thenReturn(application);
        when(repositoryProviderService.getRepositoryProvider(20L)).thenReturn(provider);
        when(gitCommandExecutor.execute(any(), any(), eq(true))).thenReturn(new GitCommandResult(0, ""));

        preparer.prepare(run(), spec, workspace, Map.of(
                "mergedBranch", "release/test/20260616140000",
                "mergedCommitSha", "abc123"));

        verify(gitCommandExecutor).execute(eq(workspace),
                eq(List.of("git", "clone", "--depth", "1", "--branch", "release/test/20260616140000",
                        "https://oauth2:token%2Fvalue@gitlab.example.com/group/repo.git", ".")),
                eq(true));
        verify(gitCommandExecutor).execute(eq(workspace),
                eq(List.of("git", "checkout", "abc123")), eq(true));
    }

    private PipelineRunDO run() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(1L);
        run.setAppId(10L);
        return run;
    }

    private PipelineSpec specWithGitlabSource() {
        PipelineSpec spec = new PipelineSpec();
        PipelineSpec.Source source = new PipelineSpec.Source();
        source.setType("gitlab");
        source.setName("代码源");
        source.setEndpoint("https://example.com/group/repo.git");
        source.setBranch("main");
        spec.setSources(new LinkedHashMap<>());
        spec.getSources().put("my_repo", source);
        return spec;
    }

    private ApplicationDO application() {
        ApplicationDO application = new ApplicationDO();
        application.setId(10L);
        application.setRepositoryProviderId(20L);
        application.setRepoUrl("https://gitlab.example.com/group/repo.git");
        application.setDefaultBranchName("master");
        return application;
    }

    private RepositoryProviderDO provider() {
        RepositoryProviderDO provider = new RepositoryProviderDO();
        provider.setId(20L);
        provider.setProviderType(RepositoryProviderTypeEnum.GITLAB.getProviderType());
        provider.setAccessToken("token/value");
        return provider;
    }

}
