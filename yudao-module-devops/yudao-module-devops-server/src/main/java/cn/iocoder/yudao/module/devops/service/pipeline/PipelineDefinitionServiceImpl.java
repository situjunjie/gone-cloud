package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineDefinitionRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineDefinitionVersionRespVO;
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
import cn.iocoder.yudao.module.devops.enums.PipelineDefinitionVersionStatusEnum;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.*;

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
    private ApplicationEnvMapper applicationEnvMapper;
    @Resource
    private PipelineSpecValidationService pipelineSpecValidationService;

    @Override
    public PipelineDefinitionRespVO getByApplicationEnvId(Long applicationEnvId) {
        PipelineDefinitionDO definition = pipelineDefinitionMapper.selectByApplicationEnvId(applicationEnvId);
        if (definition == null) {
            return null;
        }
        PipelineDefinitionRespVO respVO = PipelineConvert.INSTANCE.convert(definition);
        if (definition.getDraftVersionId() != null) {
            respVO.setDraftVersion(PipelineConvert.INSTANCE.convert(
                    pipelineDefinitionVersionMapper.selectById(definition.getDraftVersionId())));
        }
        if (definition.getPublishedVersionId() != null) {
            respVO.setPublishedVersion(PipelineConvert.INSTANCE.convert(
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
        return PipelineConvert.INSTANCE.convertVersionList(versions);
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

}
