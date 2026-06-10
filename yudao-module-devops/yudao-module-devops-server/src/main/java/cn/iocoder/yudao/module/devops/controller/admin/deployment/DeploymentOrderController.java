package cn.iocoder.yudao.module.devops.controller.admin.deployment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.deployment.vo.DeploymentOrderPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.deployment.vo.DeploymentOrderRespVO;
import cn.iocoder.yudao.module.devops.service.deployment.DeploymentOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - DevOps 部署单")
@RestController
@RequestMapping("/devops/deployment-order")
@Validated
public class DeploymentOrderController {

    @Resource
    private DeploymentOrderService deploymentOrderService;

    @GetMapping("/page")
    @Operation(summary = "获得部署单分页")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:query')")
    public CommonResult<PageResult<DeploymentOrderRespVO>> getDeploymentOrderPage(
            @Valid DeploymentOrderPageReqVO pageReqVO) {
        return success(deploymentOrderService.getDeploymentOrderPage(pageReqVO));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获得部署单详情")
    @Parameter(name = "id", description = "部署单编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:query')")
    public CommonResult<DeploymentOrderRespVO> getDeploymentOrder(@PathVariable("id") Long id) {
        return success(deploymentOrderService.getDeploymentOrder(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消部署单")
    @Parameter(name = "id", description = "部署单编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:update')")
    public CommonResult<Boolean> cancelDeploymentOrder(@PathVariable("id") Long id) {
        deploymentOrderService.cancelDeploymentOrder(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "重试部署单")
    @Parameter(name = "id", description = "部署单编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:update')")
    public CommonResult<Boolean> retryDeploymentOrder(@PathVariable("id") Long id) {
        deploymentOrderService.retryDeploymentOrder(id, getLoginUserId());
        return success(true);
    }

}
