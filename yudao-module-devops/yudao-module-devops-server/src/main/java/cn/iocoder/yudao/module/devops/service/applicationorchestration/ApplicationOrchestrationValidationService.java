package cn.iocoder.yudao.module.devops.service.applicationorchestration;

import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationConfigVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationResourceSummaryRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationValidationMessageRespVO;
import io.fabric8.kubernetes.api.model.HasMetadata;

import java.util.List;

public interface ApplicationOrchestrationValidationService {

    ValidationResult validate(ApplicationOrchestrationConfigVO config);

    record ValidationResult(ApplicationOrchestrationConfigVO normalizedConfig,
                            List<ApplicationOrchestrationResourceSummaryRespVO> resources,
                            List<ApplicationOrchestrationValidationMessageRespVO> errors) {
        public boolean valid() {
            return errors == null || errors.isEmpty();
        }
    }
}
