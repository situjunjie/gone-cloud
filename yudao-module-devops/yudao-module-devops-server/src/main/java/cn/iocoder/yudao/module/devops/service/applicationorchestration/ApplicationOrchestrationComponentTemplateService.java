package cn.iocoder.yudao.module.devops.service.applicationorchestration;

import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationTemplateRespVO;
import java.util.List;

public interface ApplicationOrchestrationComponentTemplateService {
    List<ApplicationOrchestrationTemplateRespVO> getTemplates();
}
