package cn.iocoder.yudao.module.devops.controller.admin.offlineimage;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.offlineimage.vo.OfflineImagePackagePageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.offlineimage.vo.OfflineImagePackageRespVO;
import cn.iocoder.yudao.module.devops.service.offlineimage.OfflineImagePackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - DevOps 离线镜像包")
@RestController
@RequestMapping("/devops/offline-image-package")
@Validated
public class OfflineImagePackageController {

    @Resource
    private OfflineImagePackageService offlineImagePackageService;

    @GetMapping("/page")
    @Operation(summary = "获得离线镜像包分页")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:query')")
    public CommonResult<PageResult<OfflineImagePackageRespVO>> getOfflineImagePackagePage(
            @Valid OfflineImagePackagePageReqVO pageReqVO) {
        return success(offlineImagePackageService.getOfflineImagePackagePage(pageReqVO));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获得离线镜像包详情")
    @Parameter(name = "id", description = "离线镜像包编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:query')")
    public CommonResult<OfflineImagePackageRespVO> getOfflineImagePackage(@PathVariable("id") Long id) {
        return success(offlineImagePackageService.getOfflineImagePackage(id));
    }

    @GetMapping("/{id}/download-url")
    @Operation(summary = "获得离线镜像包下载 URL")
    @Parameter(name = "id", description = "离线镜像包编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:query')")
    public CommonResult<String> getDownloadUrl(@PathVariable("id") Long id) {
        return success(offlineImagePackageService.getDownloadUrl(id));
    }

}
