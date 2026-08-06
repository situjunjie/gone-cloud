package cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationSaveDraftReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationTemplateRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationValidateReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationValidationRespVO;
import cn.iocoder.yudao.module.devops.service.applicationorchestration.ApplicationOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - DevOps 应用编排")
@RestController
@RequestMapping("/devops/application-orchestration")
@Validated
public class ApplicationOrchestrationController {

    @Resource
    private ApplicationOrchestrationService applicationOrchestrationService;

    @GetMapping("/component-templates")
    @Operation(summary = "获得应用编排组件模板列表")
    @PreAuthorize("@ss.hasPermission('devops:application-orchestration:query')")
    public CommonResult<List<ApplicationOrchestrationTemplateRespVO>> getComponentTemplates() {
        return success(applicationOrchestrationService.getComponentTemplates());
    }

    @GetMapping("/get")
    @Operation(summary = "获得应用编排配置")
    @Parameter(name = "appId", description = "应用编号", required = true, example = "1024")
    @Parameter(name = "orchestrationType", description = "编排类型", required = true, example = "K8S_DEPLOYMENT")
    @PreAuthorize("@ss.hasPermission('devops:application-orchestration:query')")
    public CommonResult<ApplicationOrchestrationRespVO> getOrchestration(
            @RequestParam("appId") Long appId,
            @RequestParam("orchestrationType") String orchestrationType) {
        return success(applicationOrchestrationService.getOrchestration(appId, orchestrationType));
    }

    @PostMapping("/validate")
    @Operation(summary = "校验应用编排配置")
    @PreAuthorize("@ss.hasPermission('devops:application-orchestration:query')")
    public CommonResult<ApplicationOrchestrationValidationRespVO> validate(
            @Valid @RequestBody ApplicationOrchestrationValidateReqVO reqVO) {
        return success(applicationOrchestrationService.validate(reqVO));
    }

    @PostMapping("/save-draft")
    @Operation(summary = "保存应用编排草稿")
    @PreAuthorize("@ss.hasPermission('devops:application-orchestration:update')")
    public CommonResult<Long> saveDraft(@Valid @RequestBody ApplicationOrchestrationSaveDraftReqVO reqVO) {
        return success(applicationOrchestrationService.saveDraft(reqVO));
    }

}
