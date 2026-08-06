package cn.iocoder.yudao.module.devops.service.applicationorchestration;

import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationSaveDraftReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationTemplateRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationValidateReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationValidationRespVO;

import java.util.List;

/**
 * DevOps 应用编排 Service 接口。
 */
public interface ApplicationOrchestrationService {

    /**
     * 获得组件模板列表。
     */
    List<ApplicationOrchestrationTemplateRespVO> getComponentTemplates();

    /**
     * 按应用与编排类型获得编排配置，不存在时返回应用摘要与空配置。
     */
    ApplicationOrchestrationRespVO getOrchestration(Long appId, String orchestrationType);

    /**
     * 校验编排配置，不落库。
     */
    ApplicationOrchestrationValidationRespVO validate(ApplicationOrchestrationValidateReqVO reqVO);

    /**
     * 校验通过后原子保存草稿，返回编排编号。
     */
    Long saveDraft(ApplicationOrchestrationSaveDraftReqVO reqVO);

}
