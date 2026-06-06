package cn.iocoder.yudao.module.devops.controller.admin.environment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentConnectionCheckRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesNamespaceRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.convert.environment.EnvironmentConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.service.environment.EnvironmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - DevOps 环境")
@RestController
@RequestMapping("/devops/environment")
@Validated
public class EnvironmentController {

    @Resource
    private EnvironmentService environmentService;

    @PostMapping("/create")
    @Operation(summary = "创建环境")
    @PreAuthorize("@ss.hasPermission('devops:environment:create')")
    public CommonResult<Long> createEnvironment(@Valid @RequestBody EnvironmentSaveReqVO createReqVO) {
        return success(environmentService.createEnvironment(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新环境")
    @PreAuthorize("@ss.hasPermission('devops:environment:update')")
    public CommonResult<Boolean> updateEnvironment(@Valid @RequestBody EnvironmentSaveReqVO updateReqVO) {
        environmentService.updateEnvironment(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除环境")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:environment:delete')")
    public CommonResult<Boolean> deleteEnvironment(@RequestParam("id") Long id) {
        environmentService.deleteEnvironment(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得环境")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public CommonResult<EnvironmentRespVO> getEnvironment(@RequestParam("id") Long id) {
        return success(EnvironmentConvert.INSTANCE.convert(environmentService.getEnvironment(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "获得环境分页")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public CommonResult<PageResult<EnvironmentRespVO>> getEnvironmentPage(@Valid EnvironmentPageReqVO pageReqVO) {
        PageResult<EnvironmentDO> pageResult = environmentService.getEnvironmentPage(pageReqVO);
        return success(EnvironmentConvert.INSTANCE.convertPage(pageResult));
    }

    @PostMapping({"/check", "/check-connection"})
    @Operation(summary = "检测环境连接")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:environment:update')")
    public CommonResult<EnvironmentConnectionCheckRespVO> checkEnvironmentConnection(@RequestParam("id") Long id) {
        return success(environmentService.checkEnvironmentConnection(id));
    }

    @GetMapping("/kubernetes/namespaces")
    @Operation(summary = "获得 Kubernetes Namespace 列表")
    @Parameter(name = "id", description = "环境编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public CommonResult<List<EnvironmentKubernetesNamespaceRespVO>> getKubernetesNamespaces(@RequestParam("id") Long id) {
        return success(environmentService.getKubernetesNamespaces(id));
    }

}
