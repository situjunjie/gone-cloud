package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineDefinitionRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineDefinitionVersionRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineCacheClearReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelinePublishReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineRollbackReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineSaveDraftReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidateReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationRespVO;
import cn.iocoder.yudao.module.devops.convert.pipeline.PipelineConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionVersionMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineDefinitionVersionStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCacheConfig;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCacheConfigResolver;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineWorkspaceService;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.devops.enums.LogRecordConstants.DEVOPS_PIPELINE_CACHE_CLEAR_SUB_TYPE;
import static cn.iocoder.yudao.module.devops.enums.LogRecordConstants.DEVOPS_PIPELINE_CACHE_CLEAR_SUCCESS;
import static cn.iocoder.yudao.module.devops.enums.LogRecordConstants.DEVOPS_PIPELINE_TYPE;

/**
 * DevOps 流水线定义 Service 实现类。
 */
@Service
@Validated
public class PipelineDefinitionServiceImpl implements PipelineDefinitionService {

    private static final String NODE_SCHEMA_VERSION = "1.0";
    private static final String DEFAULT_VERSION_NAME_PREFIX = "v";

    @Resource
    private PipelineDefinitionMapper pipelineDefinitionMapper;
    @Resource
    private PipelineDefinitionVersionMapper pipelineDefinitionVersionMapper;
    @Resource
    private PipelineRunMapper pipelineRunMapper;
    @Resource
    private ApplicationEnvMapper applicationEnvMapper;
    @Resource
    private PipelineSpecValidationService pipelineSpecValidationService;
    @Resource
    private PipelineCacheConfigResolver pipelineCacheConfigResolver;
    @Resource
    private PipelineWorkspaceService pipelineWorkspaceService;

    @Override
    public PipelineDefinitionRespVO getByApplicationEnvId(Long applicationEnvId) {
        PipelineDefinitionDO definition = pipelineDefinitionMapper.selectByApplicationEnvId(applicationEnvId);
        if (definition == null) {
            return null;
        }
        PipelineDefinitionRespVO respVO = PipelineConvert.INSTANCE.convert(definition);
        if (definition.getDraftVersionId() != null) {
            respVO.setDraftVersion(convertVersion(
                    pipelineDefinitionVersionMapper.selectById(definition.getDraftVersionId())));
        }
        if (definition.getPublishedVersionId() != null) {
            respVO.setPublishedVersion(convertVersion(
                    pipelineDefinitionVersionMapper.selectById(definition.getPublishedVersionId())));
        }
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDraft(PipelineSaveDraftReqVO reqVO) {
        ApplicationEnvDO applicationEnv = validateApplicationEnvExists(reqVO.getApplicationEnvId());
        PipelineDefinitionDO definition = pipelineDefinitionMapper.selectByApplicationEnvId(reqVO.getApplicationEnvId());
        if (definition == null) {
            definition = createDefinition(reqVO, applicationEnv);
        } else {
            definition.setName(reqVO.getName());
            definition.setRemark(reqVO.getRemark());
            pipelineDefinitionMapper.updateById(definition);
        }

        PipelineValidationRespVO validation = pipelineSpecValidationService.validate(reqVO.getSpecJson());
        PipelineDefinitionVersionDO draft = definition.getDraftVersionId() == null ? null
                : pipelineDefinitionVersionMapper.selectById(definition.getDraftVersionId());
        if (draft == null || !PipelineDefinitionVersionStatusEnum.DRAFT.getStatus().equals(draft.getVersionStatus())) {
            draft = new PipelineDefinitionVersionDO();
            draft.setDefinitionId(definition.getId());
            draft.setVersionNo(0);
            draft.setVersionStatus(PipelineDefinitionVersionStatusEnum.DRAFT.getStatus());
            fillDraft(draft, reqVO, validation);
            pipelineDefinitionVersionMapper.insert(draft);
            definition.setDraftVersionId(draft.getId());
            pipelineDefinitionMapper.updateById(definition);
            return draft.getId();
        }
        fillDraft(draft, reqVO, validation);
        pipelineDefinitionVersionMapper.updateById(draft);
        return draft.getId();
    }

    @Override
    public PipelineValidationRespVO validate(PipelineValidateReqVO reqVO) {
        pipelineCacheConfigResolver.normalizeOrDefault(reqVO.getCacheConfig());
        PipelineValidationRespVO validation = pipelineSpecValidationService.validate(reqVO.getSpecJson());
        validation.setValid(CollUtil.isEmpty(validation.getErrors()));
        return validation;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN,
            key = "#root.target.getApplicationEnvIdByDefinitionId(#reqVO.definitionId)")
    public Long publish(PipelinePublishReqVO reqVO, Long userId) {
        PipelineDefinitionDO definition = validateDefinitionExists(reqVO.getDefinitionId());
        PipelineDefinitionVersionDO draft = validateVersionExists(reqVO.getDraftVersionId());
        if (!definition.getId().equals(draft.getDefinitionId())
                || !PipelineDefinitionVersionStatusEnum.DRAFT.getStatus().equals(draft.getVersionStatus())) {
            throw exception(PIPELINE_DRAFT_NOT_EXISTS);
        }
        PipelineValidationRespVO validation = pipelineSpecValidationService.validate(draft.getSpecJson());
        if (!Boolean.TRUE.equals(validation.getValid())) {
            throw exception(PIPELINE_SPEC_INVALID);
        }

        PipelineDefinitionVersionDO published = new PipelineDefinitionVersionDO();
        published.setDefinitionId(definition.getId());
        published.setVersionNo(nextPublishedVersionNo(definition.getId()));
        published.setVersionName(StrUtil.blankToDefault(reqVO.getVersionName(),
                DEFAULT_VERSION_NAME_PREFIX + published.getVersionNo()));
        published.setVersionStatus(PipelineDefinitionVersionStatusEnum.PUBLISHED.getStatus());
        fillPublishedContent(published, draft);
        published.setValidationResultJson(JsonUtils.toJsonString(validation));
        published.setPublishedAt(LocalDateTime.now());
        published.setPublishedBy(userId);
        pipelineDefinitionVersionMapper.insert(published);

        activatePublishedVersion(definition, published);
        return published.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN,
            key = "#root.target.getApplicationEnvIdByDefinitionId(#reqVO.definitionId)")
    public Long rollback(PipelineRollbackReqVO reqVO, Long userId) {
        PipelineDefinitionDO definition = validateDefinitionExists(reqVO.getDefinitionId());
        PipelineDefinitionVersionDO targetVersion = validateVersionInDefinition(reqVO.getTargetVersionId(), definition.getId());
        if (!PipelineDefinitionVersionStatusEnum.PUBLISHED.getStatus().equals(targetVersion.getVersionStatus())) {
            throw exception(PIPELINE_ROLLBACK_TARGET_INVALID);
        }

        PipelineDefinitionVersionDO currentVersion = definition.getPublishedVersionId() == null ? null
                : validateVersionInDefinition(definition.getPublishedVersionId(), definition.getId());
        if (currentVersion != null && currentVersion.getId().equals(targetVersion.getId())) {
            throw exception(PIPELINE_ROLLBACK_TARGET_INVALID);
        }

        PipelineDefinitionVersionDO rollbackVersion = new PipelineDefinitionVersionDO();
        rollbackVersion.setDefinitionId(definition.getId());
        rollbackVersion.setVersionNo(nextPublishedVersionNo(definition.getId()));
        rollbackVersion.setVersionName(StrUtil.blankToDefault(reqVO.getVersionName(),
                DEFAULT_VERSION_NAME_PREFIX + rollbackVersion.getVersionNo()));
        rollbackVersion.setVersionStatus(PipelineDefinitionVersionStatusEnum.PUBLISHED.getStatus());
        fillPublishedContent(rollbackVersion, targetVersion);
        rollbackVersion.setRollbackFromVersionId(targetVersion.getId());
        rollbackVersion.setRollbackFromVersionNo(targetVersion.getVersionNo());
        rollbackVersion.setBasedOnCurrentVersionId(currentVersion == null ? null : currentVersion.getId());
        rollbackVersion.setBasedOnCurrentVersionNo(currentVersion == null ? null : currentVersion.getVersionNo());
        rollbackVersion.setRollbackReason(reqVO.getRollbackReason());
        rollbackVersion.setPublishedAt(LocalDateTime.now());
        rollbackVersion.setPublishedBy(userId);
        pipelineDefinitionVersionMapper.insert(rollbackVersion);

        activatePublishedVersion(definition, rollbackVersion);
        return rollbackVersion.getId();
    }

    @Override
    public List<PipelineDefinitionVersionRespVO> getVersionList(Long definitionId) {
        validateDefinitionExists(definitionId);
        List<PipelineDefinitionVersionDO> versions = pipelineDefinitionVersionMapper.selectListByDefinitionId(definitionId);
        versions.sort(Comparator.comparing(PipelineDefinitionVersionDO::getVersionNo).reversed());
        return versions.stream().map(this::convertVersion).toList();
    }

    @Override
    @LogRecord(type = DEVOPS_PIPELINE_TYPE, subType = DEVOPS_PIPELINE_CACHE_CLEAR_SUB_TYPE,
            bizNo = "{{#reqVO.definitionId}}", success = DEVOPS_PIPELINE_CACHE_CLEAR_SUCCESS)
    public Boolean clearCache(PipelineCacheClearReqVO reqVO, Long userId) {
        PipelineDefinitionDO definition = validateDefinitionExists(reqVO.getDefinitionId());
        rejectClearCacheWhenRunActive(definition);
        Set<String> allowedPaths = resolveClearableCachePaths(definition);
        Set<String> targetPaths = CollUtil.isEmpty(reqVO.getPaths())
                ? allowedPaths : normalizeRequestedCachePaths(reqVO.getPaths());
        for (String path : targetPaths) {
            if (!allowedPaths.contains(path)) {
                throw exception(PIPELINE_CACHE_CLEAR_PATH_INVALID, path);
            }
        }
        pipelineWorkspaceService.clearDefinitionCache(definition, targetPaths);
        return Boolean.TRUE;
    }

    private PipelineDefinitionDO createDefinition(PipelineSaveDraftReqVO reqVO, ApplicationEnvDO applicationEnv) {
        PipelineDefinitionDO definition = new PipelineDefinitionDO();
        definition.setName(reqVO.getName());
        definition.setDefinitionKey("app-env-" + applicationEnv.getId());
        definition.setAppId(applicationEnv.getAppId());
        definition.setApplicationEnvId(applicationEnv.getId());
        definition.setStatus(CommonStatusEnum.ENABLE.getStatus());
        definition.setRemark(reqVO.getRemark());
        pipelineDefinitionMapper.insert(definition);
        return definition;
    }

    private void fillDraft(PipelineDefinitionVersionDO draft, PipelineSaveDraftReqVO reqVO,
                           PipelineValidationRespVO validation) {
        draft.setVersionName("draft");
        draft.setVersionStatus(PipelineDefinitionVersionStatusEnum.DRAFT.getStatus());
        draft.setDiagramJson(reqVO.getDiagramJson());
        draft.setSpecJson(reqVO.getSpecJson());
        draft.setNodeSchemaVersion(NODE_SCHEMA_VERSION);
        draft.setCacheConfigJson(JsonUtils.toJsonString(pipelineCacheConfigResolver.normalizeOrDefault(reqVO.getCacheConfig())));
        draft.setValidationResultJson(JsonUtils.toJsonString(validation));
    }

    private Integer nextPublishedVersionNo(Long definitionId) {
        return pipelineDefinitionVersionMapper.selectPublishedListByDefinitionId(definitionId).stream()
                .map(PipelineDefinitionVersionDO::getVersionNo)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private void fillPublishedContent(PipelineDefinitionVersionDO published, PipelineDefinitionVersionDO source) {
        published.setDiagramJson(source.getDiagramJson());
        published.setSpecJson(source.getSpecJson());
        published.setNodeSchemaVersion(StrUtil.blankToDefault(source.getNodeSchemaVersion(), NODE_SCHEMA_VERSION));
        published.setCacheConfigJson(JsonUtils.toJsonString(pipelineCacheConfigResolver.resolveVersionConfig(source.getCacheConfigJson())));
        published.setValidationResultJson(source.getValidationResultJson());
    }

    private void activatePublishedVersion(PipelineDefinitionDO definition, PipelineDefinitionVersionDO published) {
        definition.setPublishedVersionId(published.getId());
        pipelineDefinitionMapper.updateById(definition);

        ApplicationEnvDO applicationEnv = new ApplicationEnvDO();
        applicationEnv.setId(definition.getApplicationEnvId());
        applicationEnv.setPipelineDefinitionId(definition.getId());
        applicationEnvMapper.updateById(applicationEnv);
    }

    private ApplicationEnvDO validateApplicationEnvExists(Long applicationEnvId) {
        ApplicationEnvDO applicationEnv = applicationEnvMapper.selectById(applicationEnvId);
        if (applicationEnv == null) {
            throw exception(APPLICATION_ENV_NOT_EXISTS);
        }
        return applicationEnv;
    }

    private PipelineDefinitionDO validateDefinitionExists(Long definitionId) {
        PipelineDefinitionDO definition = pipelineDefinitionMapper.selectById(definitionId);
        if (definition == null) {
            throw exception(PIPELINE_DEFINITION_NOT_EXISTS);
        }
        return definition;
    }

    private PipelineDefinitionVersionDO validateVersionInDefinition(Long versionId, Long definitionId) {
        PipelineDefinitionVersionDO version = validateVersionExists(versionId);
        if (!definitionId.equals(version.getDefinitionId())) {
            throw exception(PIPELINE_VERSION_NOT_IN_DEFINITION);
        }
        return version;
    }

    private PipelineDefinitionVersionDO validateVersionExists(Long versionId) {
        PipelineDefinitionVersionDO version = pipelineDefinitionVersionMapper.selectById(versionId);
        if (version == null) {
            throw exception(PIPELINE_VERSION_NOT_EXISTS);
        }
        return version;
    }

    public Long getApplicationEnvIdByDefinitionId(Long definitionId) {
        PipelineDefinitionDO definition = pipelineDefinitionMapper.selectById(definitionId);
        return definition == null ? null : definition.getApplicationEnvId();
    }

    private PipelineDefinitionVersionRespVO convertVersion(PipelineDefinitionVersionDO version) {
        PipelineDefinitionVersionRespVO respVO = PipelineConvert.INSTANCE.convert(version);
        if (respVO != null) {
            respVO.setCacheConfig(pipelineCacheConfigResolver.resolveVersionConfig(version.getCacheConfigJson()));
        }
        return respVO;
    }

    private void rejectClearCacheWhenRunActive(PipelineDefinitionDO definition) {
        boolean active = pipelineRunMapper.selectListByApplicationEnvIdAndStatuses(definition.getApplicationEnvId(),
                        List.of(PipelineRunStatusEnum.QUEUED.getStatus(), PipelineRunStatusEnum.RUNNING.getStatus(),
                                PipelineRunStatusEnum.WAITING_INPUT.getStatus()))
                .stream()
                .anyMatch(run -> definition.getId().equals(run.getDefinitionId()));
        if (active) {
            throw exception(PIPELINE_CACHE_CLEAR_RUNNING);
        }
    }

    private Set<String> resolveClearableCachePaths(PipelineDefinitionDO definition) {
        Set<String> paths = new LinkedHashSet<>();
        boolean hasVersionConfig = definition.getDraftVersionId() != null || definition.getPublishedVersionId() != null;
        collectCachePaths(paths, definition.getDraftVersionId());
        collectCachePaths(paths, definition.getPublishedVersionId());
        paths.addAll(pipelineWorkspaceService.listRecordedCachePaths(definition));
        if (paths.isEmpty() && !hasVersionConfig) {
            pipelineCacheConfigResolver.enabledDirectories(pipelineCacheConfigResolver.defaultConfig())
                    .forEach(directory -> paths.add(directory.getPath()));
        }
        return paths;
    }

    private void collectCachePaths(Set<String> paths, Long versionId) {
        if (versionId == null) {
            return;
        }
        PipelineDefinitionVersionDO version = pipelineDefinitionVersionMapper.selectById(versionId);
        if (version == null) {
            return;
        }
        pipelineCacheConfigResolver.enabledDirectories(
                pipelineCacheConfigResolver.resolveVersionConfig(version.getCacheConfigJson()))
                .forEach(directory -> paths.add(directory.getPath()));
    }

    private Set<String> normalizeRequestedCachePaths(List<String> requestPaths) {
        PipelineCacheConfig config = new PipelineCacheConfig();
        config.setDirectories(requestPaths.stream().map(path -> {
            PipelineCacheConfig.Directory directory = new PipelineCacheConfig.Directory();
            directory.setPath(path);
            directory.setEnabled(true);
            return directory;
        }).toList());
        Set<String> paths = new LinkedHashSet<>();
        pipelineCacheConfigResolver.enabledDirectories(config).forEach(directory -> paths.add(directory.getPath()));
        return paths;
    }

}
