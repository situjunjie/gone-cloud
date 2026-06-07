package cn.iocoder.yudao.module.devops.controller.admin.application;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseEnvDetailRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationReleaseEnvTabRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationSaveReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationUpdateEnvsReqVO;
import cn.iocoder.yudao.module.devops.convert.application.ApplicationConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.service.application.ApplicationService;
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

@Tag(name = "管理后台 - DevOps 应用")
@RestController
@RequestMapping("/devops/application")
@Validated
public class ApplicationController {

    @Resource
    private ApplicationService applicationService;

    @PostMapping("/create")
    @Operation(summary = "创建应用")
    @PreAuthorize("@ss.hasPermission('devops:application:create')")
    public CommonResult<Long> createApplication(@Valid @RequestBody ApplicationSaveReqVO createReqVO) {
        return success(applicationService.createApplication(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新应用")
    @PreAuthorize("@ss.hasPermission('devops:application:update')")
    public CommonResult<Boolean> updateApplication(@Valid @RequestBody ApplicationSaveReqVO updateReqVO) {
        applicationService.updateApplication(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除应用")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:application:delete')")
    public CommonResult<Boolean> deleteApplication(@RequestParam("id") Long id) {
        applicationService.deleteApplication(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得应用")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:application:query')")
    public CommonResult<ApplicationRespVO> getApplication(@RequestParam("id") Long id) {
        ApplicationRespVO respVO = ApplicationConvert.INSTANCE.convert(applicationService.getApplication(id));
        if (respVO != null) {
            respVO.setEnvs(applicationService.getApplicationEnvList(id));
        }
        return success(respVO);
    }

    @GetMapping("/page")
    @Operation(summary = "获得应用分页")
    @PreAuthorize("@ss.hasPermission('devops:application:query')")
    public CommonResult<PageResult<ApplicationRespVO>> getApplicationPage(@Valid ApplicationPageReqVO pageReqVO) {
        PageResult<ApplicationDO> pageResult = applicationService.getApplicationPage(pageReqVO);
        return success(ApplicationConvert.INSTANCE.convertPage(pageResult));
    }

    @PutMapping("/update-envs")
    @Operation(summary = "替换应用环境")
    @PreAuthorize("@ss.hasPermission('devops:application:update')")
    public CommonResult<Boolean> updateApplicationEnvs(@Valid @RequestBody ApplicationUpdateEnvsReqVO updateReqVO) {
        applicationService.updateApplicationEnvs(updateReqVO);
        return success(true);
    }

    @GetMapping("/release/env-tabs")
    @Operation(summary = "获得应用发布环境 Tab")
    @Parameter(name = "appId", description = "应用编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:application:query')")
    public CommonResult<List<ApplicationReleaseEnvTabRespVO>> getApplicationReleaseEnvTabs(
            @RequestParam("appId") Long appId) {
        return success(applicationService.getApplicationReleaseEnvTabs(appId));
    }

    @GetMapping("/release/env-detail")
    @Operation(summary = "获得应用发布环境详情")
    @Parameter(name = "applicationEnvId", description = "应用环境关系编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:application:query')")
    public CommonResult<ApplicationReleaseEnvDetailRespVO> getApplicationReleaseEnvDetail(
            @RequestParam("applicationEnvId") Long applicationEnvId) {
        return success(applicationService.getApplicationReleaseEnvDetail(applicationEnvId));
    }

}
