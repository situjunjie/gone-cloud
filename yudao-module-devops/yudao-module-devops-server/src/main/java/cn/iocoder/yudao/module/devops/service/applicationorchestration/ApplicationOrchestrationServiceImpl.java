package cn.iocoder.yudao.module.devops.service.applicationorchestration;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationConfigVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationSaveDraftReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationTemplateRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationValidateReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationValidationRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.applicationorchestration.ApplicationOrchestrationDO;
import cn.iocoder.yudao.module.devops.dal.mysql.applicationorchestration.ApplicationOrchestrationMapper;
import cn.iocoder.yudao.module.devops.enums.ApplicationOrchestrationTypeEnum;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_ORCHESTRATION_INVALID;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_ORCHESTRATION_TYPE_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_ORCHESTRATION_UPDATE_CONFLICT;

/**
 * DevOps 应用编排 Service 实现类。
 */
@Service
@Validated
public class ApplicationOrchestrationServiceImpl implements ApplicationOrchestrationService {

    @Resource
    private ApplicationOrchestrationMapper applicationOrchestrationMapper;

    @Resource
    private ApplicationOrchestrationComponentTemplateService componentTemplateService;

    @Resource
    private ApplicationOrchestrationValidationService validationService;

    @Resource
    private ApplicationMapper applicationMapper;

    @Override
    public List<ApplicationOrchestrationTemplateRespVO> getComponentTemplates() {
        return componentTemplateService.getTemplates();
    }

    @Override
    public ApplicationOrchestrationRespVO getOrchestration(Long appId, String orchestrationType) {
        validateOrchestrationType(orchestrationType);
        ApplicationDO application = validateApplicationExists(appId);
        ApplicationOrchestrationDO orchestration = applicationOrchestrationMapper.selectByAppIdAndType(appId, orchestrationType);

        ApplicationOrchestrationRespVO respVO = new ApplicationOrchestrationRespVO();
        respVO.setAppId(application.getId());
        respVO.setAppKey(application.getAppKey());
        respVO.setAppName(application.getName());
        respVO.setOrchestrationType(orchestrationType);
        if (orchestration == null) {
            respVO.setConfig(new ApplicationOrchestrationConfigVO());
            return respVO;
        }
        respVO.setId(orchestration.getId());
        respVO.setConfig(parseConfig(orchestration.getComponentJson()));
        respVO.setCanvasJson(orchestration.getCanvasJson());
        respVO.setUpdateTime(orchestration.getUpdateTime());
        return respVO;
    }

    @Override
    public ApplicationOrchestrationValidationRespVO validate(ApplicationOrchestrationValidateReqVO reqVO) {
        validateOrchestrationType(reqVO.getOrchestrationType());
        validateApplicationExists(reqVO.getAppId());
        return toValidationResp(validationService.validate(reqVO.getConfig()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDraft(ApplicationOrchestrationSaveDraftReqVO reqVO) {
        validateOrchestrationType(reqVO.getOrchestrationType());
        validateApplicationExists(reqVO.getAppId());

        ApplicationOrchestrationValidationService.ValidationResult result = validationService.validate(reqVO.getConfig());
        if (!result.valid()) {
            throw exception(APPLICATION_ORCHESTRATION_INVALID, buildErrorSummary(result));
        }
        ApplicationOrchestrationValidationRespVO validationResp = toValidationResp(result);

        ApplicationOrchestrationDO existing = applicationOrchestrationMapper.selectByAppIdAndType(
                reqVO.getAppId(), reqVO.getOrchestrationType());
        if (existing == null) {
            ApplicationOrchestrationDO orchestration = new ApplicationOrchestrationDO();
            orchestration.setAppId(reqVO.getAppId());
            orchestration.setOrchestrationType(reqVO.getOrchestrationType());
            orchestration.setSchemaVersion(result.normalizedConfig().getSchemaVersion());
            orchestration.setComponentJson(JsonUtils.toJsonString(result.normalizedConfig()));
            orchestration.setCanvasJson(reqVO.getCanvasJson());
            orchestration.setValidationResultJson(JsonUtils.toJsonString(validationResp));
            applicationOrchestrationMapper.insert(orchestration);
            return orchestration.getId();
        }

        ApplicationOrchestrationDO updateObj = new ApplicationOrchestrationDO();
        updateObj.setId(existing.getId());
        updateObj.setSchemaVersion(result.normalizedConfig().getSchemaVersion());
        updateObj.setComponentJson(JsonUtils.toJsonString(result.normalizedConfig()));
        updateObj.setCanvasJson(reqVO.getCanvasJson());
        updateObj.setValidationResultJson(JsonUtils.toJsonString(validationResp));
        int updated = applicationOrchestrationMapper.updateByIdAndUpdateTime(updateObj, existing.getUpdateTime());
        if (updated == 0) {
            throw exception(APPLICATION_ORCHESTRATION_UPDATE_CONFLICT);
        }
        return existing.getId();
    }

    // 配置服务不能依赖含流水线启动能力的 ApplicationService，否则部署读取编排时形成 Bean 环。
    private ApplicationDO validateApplicationExists(Long appId) {
        ApplicationDO application = applicationMapper.selectById(appId);
        if (application == null) {
            throw exception(APPLICATION_NOT_EXISTS);
        }
        return application;
    }

    private void validateOrchestrationType(String orchestrationType) {
        if (!ApplicationOrchestrationTypeEnum.isValid(orchestrationType)) {
            throw exception(APPLICATION_ORCHESTRATION_TYPE_NOT_SUPPORTED);
        }
    }

    private ApplicationOrchestrationConfigVO parseConfig(String componentJson) {
        ApplicationOrchestrationConfigVO config = JsonUtils.parseObject(componentJson, ApplicationOrchestrationConfigVO.class);
        return config == null ? new ApplicationOrchestrationConfigVO() : config;
    }

    private ApplicationOrchestrationValidationRespVO toValidationResp(ApplicationOrchestrationValidationService.ValidationResult result) {
        ApplicationOrchestrationValidationRespVO respVO = new ApplicationOrchestrationValidationRespVO();
        respVO.setValid(result.valid());
        respVO.setNormalizedConfig(result.normalizedConfig());
        respVO.setResources(result.resources());
        respVO.setErrors(result.errors());
        return respVO;
    }

    private String buildErrorSummary(ApplicationOrchestrationValidationService.ValidationResult result) {
        if (CollUtil.isEmpty(result.errors())) {
            return "";
        }
        return result.errors().stream()
                .map(error -> (error.getComponentId() == null ? "" : error.getComponentId() + ": ") + error.getMessage())
                .limit(5)
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }

}
