package cn.iocoder.yudao.module.devops.service.repositoryprovider;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo.RepositoryProviderPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo.RepositoryProviderProjectRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo.RepositoryProviderSaveReqVO;
import cn.iocoder.yudao.module.devops.convert.repositoryprovider.RepositoryProviderConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.repositoryprovider.RepositoryProviderMapper;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderAuthTypeEnum;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderCheckStatusEnum;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderTypeEnum;
import jakarta.annotation.Resource;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.User;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.*;

/**
 * DevOps 代码源 Service 实现类。
 */
@Service
@Validated
public class RepositoryProviderServiceImpl implements RepositoryProviderService {

    private static final int PROJECT_PAGE = 1;
    private static final int PROJECT_PAGE_SIZE = 20;

    @Resource
    private RepositoryProviderMapper repositoryProviderMapper;
    @Resource
    private ApplicationMapper applicationMapper;

    @Override
    public Long createRepositoryProvider(RepositoryProviderSaveReqVO createReqVO) {
        validateNameUnique(null, createReqVO.getName());
        validateProviderConfig(createReqVO);
        if (StrUtil.isBlank(createReqVO.getAccessToken())) {
            throw exception(REPOSITORY_PROVIDER_ACCESS_TOKEN_REQUIRED);
        }

        RepositoryProviderDO provider = RepositoryProviderConvert.INSTANCE.convert(createReqVO);
        provider.setServerUrl(normalizeUrl(provider.getServerUrl()));
        provider.setApiUrl(normalizeUrl(provider.getApiUrl()));
        provider.setTokenMask(maskToken(provider.getAccessToken()));
        repositoryProviderMapper.insert(provider);
        return provider.getId();
    }

    @Override
    public void updateRepositoryProvider(RepositoryProviderSaveReqVO updateReqVO) {
        RepositoryProviderDO oldProvider = validateRepositoryProviderExists(updateReqVO.getId());
        validateNameUnique(updateReqVO.getId(), updateReqVO.getName());
        validateProviderConfig(updateReqVO);

        RepositoryProviderDO updateObj = RepositoryProviderConvert.INSTANCE.convert(updateReqVO);
        updateObj.setServerUrl(normalizeUrl(updateObj.getServerUrl()));
        updateObj.setApiUrl(normalizeUrl(updateObj.getApiUrl()));
        if (StrUtil.isBlank(updateReqVO.getAccessToken())) {
            updateObj.setAccessToken(oldProvider.getAccessToken());
            updateObj.setTokenMask(oldProvider.getTokenMask());
        } else {
            updateObj.setTokenMask(maskToken(updateReqVO.getAccessToken()));
        }
        repositoryProviderMapper.updateById(updateObj);
    }

    @Override
    public void deleteRepositoryProvider(Long id) {
        validateRepositoryProviderExists(id);
        if (applicationMapper.selectCountByRepositoryProviderId(id) > 0) {
            throw exception(REPOSITORY_PROVIDER_DELETE_FAIL_APPLICATION_EXISTS);
        }
        repositoryProviderMapper.deleteById(id);
    }

    @Override
    public RepositoryProviderDO getRepositoryProvider(Long id) {
        return repositoryProviderMapper.selectById(id);
    }

    @Override
    public PageResult<RepositoryProviderDO> getRepositoryProviderPage(RepositoryProviderPageReqVO pageReqVO) {
        return repositoryProviderMapper.selectPage(pageReqVO);
    }

    @Override
    public RepositoryProviderDO validateRepositoryProviderExists(Long id) {
        RepositoryProviderDO provider = repositoryProviderMapper.selectById(id);
        if (provider == null) {
            throw exception(REPOSITORY_PROVIDER_NOT_EXISTS);
        }
        return provider;
    }

    @Override
    public void checkRepositoryProvider(Long id) {
        RepositoryProviderDO provider = validateRepositoryProviderExists(id);
        try (GitLabApi gitLabApi = createGitLabApi(provider)) {
            User user = gitLabApi.getUserApi().getCurrentUser();
            updateCheckResult(id, RepositoryProviderCheckStatusEnum.SUCCESS.getStatus(),
                    StrUtil.format("连接成功，当前用户：{}", user.getUsername()));
        } catch (GitLabApiException ex) {
            String message = StrUtil.subPre(ex.getMessage(), 512);
            updateCheckResult(id, RepositoryProviderCheckStatusEnum.FAIL.getStatus(), message);
            throw exception(REPOSITORY_PROVIDER_GITLAB_CONNECTION_FAIL, message);
        }
    }

    @Override
    public List<RepositoryProviderProjectRespVO> getRepositoryProviderProjects(Long id) {
        RepositoryProviderDO provider = validateRepositoryProviderExists(id);
        try (GitLabApi gitLabApi = createGitLabApi(provider)) {
            List<Project> projects = gitLabApi.getProjectApi()
                    .getMemberProjects(PROJECT_PAGE, PROJECT_PAGE_SIZE);
            return projects.stream().map(this::convertProject).toList();
        } catch (GitLabApiException ex) {
            throw exception(REPOSITORY_PROVIDER_GITLAB_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    private void validateNameUnique(Long id, String name) {
        RepositoryProviderDO provider = repositoryProviderMapper.selectByName(name);
        if (provider != null && !provider.getId().equals(id)) {
            throw exception(REPOSITORY_PROVIDER_NAME_DUPLICATE);
        }
    }

    private void validateProviderConfig(RepositoryProviderSaveReqVO reqVO) {
        if (!RepositoryProviderTypeEnum.GITLAB.getProviderType().equals(reqVO.getProviderType())) {
            throw exception(REPOSITORY_PROVIDER_TYPE_NOT_SUPPORTED);
        }
        if (!RepositoryProviderAuthTypeEnum.ACCESS_TOKEN.getAuthType().equals(reqVO.getAuthType())) {
            throw exception(REPOSITORY_PROVIDER_AUTH_TYPE_NOT_SUPPORTED);
        }
    }

    private GitLabApi createGitLabApi(RepositoryProviderDO provider) {
        if (StrUtil.isBlank(provider.getAccessToken())) {
            throw exception(REPOSITORY_PROVIDER_ACCESS_TOKEN_REQUIRED);
        }
        return new GitLabApi(provider.getServerUrl(), provider.getAccessToken());
    }

    private void updateCheckResult(Long id, Integer status, String message) {
        RepositoryProviderDO updateObj = new RepositoryProviderDO();
        updateObj.setId(id);
        updateObj.setLastCheckTime(LocalDateTime.now());
        updateObj.setLastCheckStatus(status);
        updateObj.setLastCheckMessage(message);
        repositoryProviderMapper.updateById(updateObj);
    }

    private RepositoryProviderProjectRespVO convertProject(Project project) {
        RepositoryProviderProjectRespVO respVO = new RepositoryProviderProjectRespVO();
        respVO.setExternalProjectId(project.getId());
        respVO.setName(project.getName());
        respVO.setPathWithNamespace(project.getPathWithNamespace());
        respVO.setWebUrl(project.getWebUrl());
        respVO.setHttpUrlToRepo(project.getHttpUrlToRepo());
        respVO.setSshUrlToRepo(project.getSshUrlToRepo());
        respVO.setDefaultBranch(project.getDefaultBranch());
        respVO.setVisibility(project.getVisibility() == null ? null : project.getVisibility().toValue());
        return respVO;
    }

    private String normalizeUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return url;
        }
        return StrUtil.removeSuffix(url.trim(), "/");
    }

    private String maskToken(String token) {
        if (StrUtil.isBlank(token)) {
            return null;
        }
        String suffix = StrUtil.subSuf(token, Math.max(token.length() - 4, 0));
        return "****" + suffix;
    }

}
