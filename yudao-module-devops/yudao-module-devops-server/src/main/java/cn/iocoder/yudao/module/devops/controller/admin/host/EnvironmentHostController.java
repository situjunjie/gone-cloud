package cn.iocoder.yudao.module.devops.controller.admin.host;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostSaveReqVO;
import cn.iocoder.yudao.module.devops.convert.host.EnvironmentHostConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import cn.iocoder.yudao.module.devops.service.host.EnvironmentHostService;
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

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - DevOps 环境主机")
@RestController
@RequestMapping("/devops/environment/host")
@Validated
public class EnvironmentHostController {

    @Resource
    private EnvironmentHostService environmentHostService;

    @PostMapping("/create")
    @Operation(summary = "创建环境主机")
    @PreAuthorize("@ss.hasPermission('devops:environment:create')")
    public CommonResult<Long> createHost(@Valid @RequestBody EnvironmentHostSaveReqVO createReqVO) {
        return success(environmentHostService.createHost(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新环境主机")
    @PreAuthorize("@ss.hasPermission('devops:environment:update')")
    public CommonResult<Boolean> updateHost(@Valid @RequestBody EnvironmentHostSaveReqVO updateReqVO) {
        environmentHostService.updateHost(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除环境主机")
    @Parameter(name = "id", description = "主机编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:environment:delete')")
    public CommonResult<Boolean> deleteHost(@RequestParam("id") Long id) {
        environmentHostService.deleteHost(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得环境主机")
    @Parameter(name = "id", description = "主机编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public CommonResult<EnvironmentHostRespVO> getHost(@RequestParam("id") Long id) {
        return success(EnvironmentHostConvert.INSTANCE.convert(environmentHostService.getHost(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "获得环境主机分页")
    @PreAuthorize("@ss.hasPermission('devops:environment:query')")
    public CommonResult<PageResult<EnvironmentHostRespVO>> getHostPage(@Valid EnvironmentHostPageReqVO pageReqVO) {
        PageResult<EnvironmentHostDO> pageResult = environmentHostService.getHostPage(pageReqVO);
        return success(EnvironmentHostConvert.INSTANCE.convertPage(pageResult));
    }

    @PostMapping("/check")
    @Operation(summary = "检测环境主机 SSH 连接")
    @Parameter(name = "id", description = "主机编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:environment:update')")
    public CommonResult<EnvironmentHostRespVO> checkHost(@RequestParam("id") Long id) {
        return success(EnvironmentHostConvert.INSTANCE.convert(environmentHostService.checkHost(id)));
    }

}
