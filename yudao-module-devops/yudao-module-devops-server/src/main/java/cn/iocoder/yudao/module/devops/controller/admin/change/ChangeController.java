package cn.iocoder.yudao.module.devops.controller.admin.change;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeCreateFromApplicationReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeDiscardReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeEnvMountReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeEnvUnmountReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangePageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeSaveReqVO;
import cn.iocoder.yudao.module.devops.convert.change.ChangeConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeDO;
import cn.iocoder.yudao.module.devops.service.change.ChangeService;
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
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - DevOps 变更")
@RestController
@RequestMapping("/devops/change")
@Validated
public class ChangeController {

    @Resource
    private ChangeService changeService;

    @PostMapping("/create")
    @Operation(summary = "创建变更")
    @PreAuthorize("@ss.hasPermission('devops:change:create')")
    public CommonResult<Long> createChange(@Valid @RequestBody ChangeSaveReqVO createReqVO) {
        return success(changeService.createChange(createReqVO));
    }

    @PostMapping("/create-from-application")
    @Operation(summary = "从应用详情页创建变更")
    @PreAuthorize("@ss.hasPermission('devops:change:create')")
    public CommonResult<Long> createChangeFromApplication(@Valid @RequestBody ChangeCreateFromApplicationReqVO createReqVO) {
        return success(changeService.createChangeFromApplication(createReqVO, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新变更")
    @PreAuthorize("@ss.hasPermission('devops:change:update')")
    public CommonResult<Boolean> updateChange(@Valid @RequestBody ChangeSaveReqVO updateReqVO) {
        changeService.updateChange(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除变更")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:change:delete')")
    public CommonResult<Boolean> deleteChange(@RequestParam("id") Long id) {
        changeService.deleteChange(id);
        return success(true);
    }

    @PutMapping("/release")
    @Operation(summary = "发布变更")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:change:update')")
    public CommonResult<Boolean> releaseChange(@RequestParam("id") Long id) {
        changeService.releaseChange(id);
        return success(true);
    }

    @PutMapping("/discard")
    @Operation(summary = "废弃变更")
    @PreAuthorize("@ss.hasPermission('devops:change:update')")
    public CommonResult<Boolean> discardChange(@Valid @RequestBody ChangeDiscardReqVO discardReqVO) {
        changeService.discardChange(discardReqVO);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得变更")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:change:query')")
    public CommonResult<ChangeRespVO> getChange(@RequestParam("id") Long id) {
        ChangeRespVO respVO = ChangeConvert.INSTANCE.convert(changeService.getChange(id));
        if (respVO != null) {
            respVO.setEnvs(changeService.getChangeEnvList(id));
        }
        return success(respVO);
    }

    @GetMapping("/page")
    @Operation(summary = "获得变更分页")
    @PreAuthorize("@ss.hasPermission('devops:change:query')")
    public CommonResult<PageResult<ChangeRespVO>> getChangePage(@Valid ChangePageReqVO pageReqVO) {
        PageResult<ChangeDO> pageResult = changeService.getChangePage(pageReqVO);
        return success(ChangeConvert.INSTANCE.convertPage(pageResult));
    }

    @PostMapping("/mount-env")
    @Operation(summary = "挂载变更环境")
    @PreAuthorize("@ss.hasPermission('devops:change:mount-env')")
    public CommonResult<Long> mountChangeEnv(@Valid @RequestBody ChangeEnvMountReqVO mountReqVO) {
        return success(changeService.mountChangeEnv(mountReqVO, getLoginUserId()));
    }

    @PutMapping("/unmount-env")
    @Operation(summary = "移除变更环境")
    @PreAuthorize("@ss.hasPermission('devops:change:unmount-env')")
    public CommonResult<Boolean> unmountChangeEnv(@Valid @RequestBody ChangeEnvUnmountReqVO unmountReqVO) {
        changeService.unmountChangeEnv(unmountReqVO, getLoginUserId());
        return success(true);
    }

}
