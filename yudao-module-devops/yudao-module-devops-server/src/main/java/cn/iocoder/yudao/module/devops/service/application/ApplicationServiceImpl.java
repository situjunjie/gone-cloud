package cn.iocoder.yudao.module.devops.service.application;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationEnvRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationEnvSaveReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationSaveReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationUpdateEnvsReqVO;
import cn.iocoder.yudao.module.devops.convert.application.ApplicationConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.change.ChangeMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.environment.EnvironmentMapper;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.*;

/**
 * DevOps 应用 Service 实现类。
 */
@Service
@Validated
public class ApplicationServiceImpl implements ApplicationService {

    @Resource
    private ApplicationMapper applicationMapper;
    @Resource
    private ApplicationEnvMapper applicationEnvMapper;
    @Resource
    private EnvironmentMapper environmentMapper;
    @Resource
    private ChangeMapper changeMapper;
    @Resource
    private RepositoryProviderService repositoryProviderService;

    @Override
    public Long createApplication(ApplicationSaveReqVO createReqVO) {
        RepositoryProviderDO repositoryProvider = repositoryProviderService
                .validateRepositoryProviderExists(createReqVO.getRepositoryProviderId());
        validateApplicationUnique(null, createReqVO.getAppKey(),
                createReqVO.getRepositoryProviderId(), createReqVO.getRepoIdentifier());

        ApplicationDO application = ApplicationConvert.INSTANCE.convert(createReqVO);
        application.setRepoProviderType(repositoryProvider.getProviderType());
        applicationMapper.insert(application);
        return application.getId();
    }

    @Override
    public void updateApplication(ApplicationSaveReqVO updateReqVO) {
        validateApplicationExists(updateReqVO.getId());
        RepositoryProviderDO repositoryProvider = repositoryProviderService
                .validateRepositoryProviderExists(updateReqVO.getRepositoryProviderId());
        validateApplicationUnique(updateReqVO.getId(), updateReqVO.getAppKey(),
                updateReqVO.getRepositoryProviderId(), updateReqVO.getRepoIdentifier());

        ApplicationDO updateObj = ApplicationConvert.INSTANCE.convert(updateReqVO);
        updateObj.setRepoProviderType(repositoryProvider.getProviderType());
        applicationMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteApplication(Long id) {
        validateApplicationExists(id);
        if (changeMapper.selectCount(ChangeDO::getAppId, id) > 0) {
            throw exception(APPLICATION_DELETE_FAIL_CHANGE_EXISTS);
        }

        applicationEnvMapper.deleteByAppId(id);
        applicationMapper.deleteById(id);
    }

    @Override
    public ApplicationDO getApplication(Long id) {
        return applicationMapper.selectById(id);
    }

    @Override
    public PageResult<ApplicationDO> getApplicationPage(ApplicationPageReqVO pageReqVO) {
        return applicationMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateApplicationEnvs(ApplicationUpdateEnvsReqVO updateReqVO) {
        validateApplicationExists(updateReqVO.getAppId());
        validateApplicationEnvList(updateReqVO.getEnvs());

        applicationEnvMapper.deleteByAppId(updateReqVO.getAppId());
        if (CollUtil.isEmpty(updateReqVO.getEnvs())) {
            return;
        }
        List<ApplicationEnvDO> envs = updateReqVO.getEnvs().stream().map(envReqVO -> {
            ApplicationEnvDO env = ApplicationConvert.INSTANCE.convert(envReqVO);
            env.setAppId(updateReqVO.getAppId());
            return env;
        }).toList();
        applicationEnvMapper.insertBatch(envs);
    }

    @Override
    public List<ApplicationEnvRespVO> getApplicationEnvList(Long appId) {
        return ApplicationConvert.INSTANCE.convertEnvList(applicationEnvMapper.selectListByAppId(appId));
    }

    @Override
    public ApplicationDO validateApplicationExists(Long id) {
        ApplicationDO application = applicationMapper.selectById(id);
        if (application == null) {
            throw exception(APPLICATION_NOT_EXISTS);
        }
        return application;
    }

    private void validateApplicationUnique(Long id, String appKey, Long repositoryProviderId, String repoIdentifier) {
        ApplicationDO appKeyApplication = applicationMapper.selectByAppKey(appKey);
        if (appKeyApplication != null && !appKeyApplication.getId().equals(id)) {
            throw exception(APPLICATION_APP_KEY_DUPLICATE);
        }
        ApplicationDO repoIdentifierApplication = applicationMapper
                .selectByRepositoryProviderIdAndRepoIdentifier(repositoryProviderId, repoIdentifier);
        if (repoIdentifierApplication != null && !repoIdentifierApplication.getId().equals(id)) {
            throw exception(APPLICATION_REPO_IDENTIFIER_DUPLICATE);
        }
    }

    private void validateApplicationEnvList(List<ApplicationEnvSaveReqVO> envs) {
        if (CollUtil.isEmpty(envs)) {
            return;
        }
        Set<Long> envIds = new HashSet<>();
        for (ApplicationEnvSaveReqVO env : envs) {
            if (!envIds.add(env.getEnvId())) {
                throw exception(APPLICATION_ENV_DUPLICATE);
            }
            if (environmentMapper.selectById(env.getEnvId()) == null) {
                throw exception(ENVIRONMENT_NOT_EXISTS);
            }
        }
    }

}
