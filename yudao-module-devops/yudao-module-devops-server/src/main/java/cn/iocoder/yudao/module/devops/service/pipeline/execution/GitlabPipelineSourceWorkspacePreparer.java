package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderTypeEnum;
import cn.iocoder.yudao.module.devops.framework.git.GitCommandException;
import cn.iocoder.yudao.module.devops.framework.git.GitCommandExecutor;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * GitLab 源码工作目录准备器。
 */
@Component
public class GitlabPipelineSourceWorkspacePreparer implements PipelineSourceWorkspacePreparer {

    private static final String SOURCE_TYPE_GITLAB = "gitlab";
    private static final String TRIGGER_TYPE_APPLICATION_UPLOAD_IMAGE = "APPLICATION_UPLOAD_IMAGE";

    @Resource
    private ApplicationMapper applicationMapper;
    @Resource
    private RepositoryProviderService repositoryProviderService;
    @Resource
    private GitCommandExecutor gitCommandExecutor;

    @Override
    public void prepare(PipelineRunDO run, PipelineSpec spec, Path workspace, Map<String, Object> sharedState) {
        PipelineSpec.Source source = firstSource(spec);
        if (source != null && !SOURCE_TYPE_GITLAB.equalsIgnoreCase(source.getType())) {
            return;
        }
        if (source == null && run.getAppId() == null) {
            return;
        }
        if (source == null && TRIGGER_TYPE_APPLICATION_UPLOAD_IMAGE.equals(run.getTriggerType())) {
            return;
        }
        if (Files.exists(workspace.resolve(".git"))) {
            return;
        }
        SourceCheckout checkout = resolveCheckout(run, source, sharedState);
        String branch = checkout.branch();
        if (StrUtil.isBlank(branch)) {
            throw new IllegalStateException("Source branch is required");
        }
        try {
            initializeWorkspaceRepository(workspace, checkout.repoUrl());
            gitCommandExecutor.execute(workspace,
                    List.of("git", "fetch", "--depth", "1", "origin", branch),
                    true);
            gitCommandExecutor.execute(workspace,
                    List.of("git", "checkout", "-B", branch, "FETCH_HEAD"),
                    true);
            if (StrUtil.isNotBlank(checkout.commitSha())) {
                gitCommandExecutor.execute(workspace, List.of("git", "fetch", "--depth", "1", "origin",
                        checkout.commitSha()), false);
                gitCommandExecutor.execute(workspace, List.of("git", "checkout", checkout.commitSha()), true);
            }
        } catch (GitCommandException ex) {
            throw new IllegalStateException("Prepare source workspace failed: " + sanitizeGitOutput(ex.getOutput()), ex);
        }
    }

    private void initializeWorkspaceRepository(Path workspace, String repoUrl) {
        gitCommandExecutor.execute(workspace, List.of("git", "init"), true);
        gitCommandExecutor.execute(workspace, List.of("git", "remote", "add", "origin", repoUrl), true);
    }

    private PipelineSpec.Source firstSource(PipelineSpec spec) {
        if (spec == null || CollUtil.isEmpty(spec.getSources())) {
            return null;
        }
        return spec.getSources().values().iterator().next();
    }

    private SourceCheckout resolveCheckout(PipelineRunDO run, PipelineSpec.Source source, Map<String, Object> sharedState) {
        String mergedBranch = getString(sharedState, "mergedBranch");
        String mergedCommitSha = getString(sharedState, "mergedCommitSha");
        if (run.getAppId() == null) {
            return new SourceCheckout(source.getEndpoint(), StrUtil.blankToDefault(mergedBranch, source.getBranch()),
                    mergedCommitSha);
        }
        ApplicationDO application = getApplication(run);
        RepositoryProviderDO provider = getRepositoryProvider(application);
        return new SourceCheckout(buildAuthenticatedRepoUrl(application.getRepoUrl(), provider.getAccessToken()),
                firstNotBlank(mergedBranch, application.getDefaultBranchName(), source == null ? null : source.getBranch()),
                mergedCommitSha);
    }

    private ApplicationDO getApplication(PipelineRunDO run) {
        ApplicationDO application = applicationMapper.selectById(run.getAppId());
        if (application == null) {
            throw new IllegalStateException("Application not found for source checkout");
        }
        if (application.getRepositoryProviderId() == null || StrUtil.isBlank(application.getRepoUrl())) {
            throw new IllegalStateException("Application repository is required for source checkout");
        }
        return application;
    }

    private RepositoryProviderDO getRepositoryProvider(ApplicationDO application) {
        RepositoryProviderDO provider = repositoryProviderService.getRepositoryProvider(application.getRepositoryProviderId());
        if (provider == null) {
            throw new IllegalStateException("Repository provider not found for source checkout");
        }
        if (!RepositoryProviderTypeEnum.GITLAB.getProviderType().equals(provider.getProviderType())) {
            throw new IllegalStateException("Only GitLab source checkout is supported");
        }
        if (StrUtil.isBlank(provider.getAccessToken())) {
            throw new IllegalStateException("Repository access token is required for source checkout");
        }
        return provider;
    }

    private String buildAuthenticatedRepoUrl(String repoUrl, String accessToken) {
        URI uri = URI.create(repoUrl);
        String token = URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
        String path = StrUtil.nullToEmpty(uri.getRawPath());
        return uri.getScheme() + "://oauth2:" + token + "@" + uri.getAuthority() + path;
    }

    private String sanitizeGitOutput(String output) {
        if (StrUtil.isBlank(output)) {
            return "git command failed";
        }
        return StrUtil.subPre(output.replaceAll("oauth2:[^@\\s]+@", "oauth2:****@"), 1000);
    }

    private String getString(Map<String, Object> sharedState, String key) {
        if (sharedState == null) {
            return null;
        }
        Object value = sharedState.get(key);
        return value instanceof String str ? str : null;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private record SourceCheckout(String repoUrl, String branch, String commitSha) {
    }

}
