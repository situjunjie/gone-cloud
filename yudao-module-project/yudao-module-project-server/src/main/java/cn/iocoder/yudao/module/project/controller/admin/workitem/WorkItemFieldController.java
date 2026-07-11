package cn.iocoder.yudao.module.project.controller.admin.workitem;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldOptionSaveReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldSaveReqVO;
import cn.iocoder.yudao.module.project.service.workitem.WorkItemFieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 项目工作项自定义字段")
@RestController
@RequestMapping("/project/work-item-field")
@Validated
public class WorkItemFieldController {

    @Resource
    private WorkItemFieldService fieldService;

    @PostMapping("/create")
    @Operation(summary = "创建工作项自定义字段")
    @PreAuthorize("@ss.hasPermission('project:work-item:update')")
    public CommonResult<Long> createField(@Valid @RequestBody WorkItemFieldSaveReqVO reqVO) {
        return success(fieldService.createField(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新工作项自定义字段")
    @PreAuthorize("@ss.hasPermission('project:work-item:update')")
    public CommonResult<Boolean> updateField(@Valid @RequestBody WorkItemFieldSaveReqVO reqVO) {
        fieldService.updateField(reqVO);
        return success(true);
    }

    @PutMapping("/update-enabled")
    @Operation(summary = "启用或禁用工作项自定义字段")
    @PreAuthorize("@ss.hasPermission('project:work-item:update')")
    public CommonResult<Boolean> updateFieldEnabled(@RequestParam("id") @NotNull Long id,
                                                    @RequestParam("enabled") @NotNull Boolean enabled) {
        fieldService.updateFieldEnabled(id, enabled);
        return success(true);
    }

    @GetMapping("/list")
    @Operation(summary = "获得项目工作项自定义字段列表")
    @PreAuthorize("@ss.hasPermission('project:work-item:query')")
    public CommonResult<List<WorkItemFieldRespVO>> getFieldList(@RequestParam("projectId") Long projectId) {
        return success(fieldService.getFieldList(projectId));
    }

    @PostMapping("/option/create")
    @Operation(summary = "创建工作项自定义字段选项")
    @PreAuthorize("@ss.hasPermission('project:work-item:update')")
    public CommonResult<Long> createOption(@Valid @RequestBody WorkItemFieldOptionSaveReqVO reqVO) {
        return success(fieldService.createOption(reqVO));
    }

    @PutMapping("/option/update")
    @Operation(summary = "更新工作项自定义字段选项")
    @PreAuthorize("@ss.hasPermission('project:work-item:update')")
    public CommonResult<Boolean> updateOption(@Valid @RequestBody WorkItemFieldOptionSaveReqVO reqVO) {
        fieldService.updateOption(reqVO);
        return success(true);
    }

    @PutMapping("/option/update-enabled")
    @Operation(summary = "启用或禁用工作项自定义字段选项")
    @PreAuthorize("@ss.hasPermission('project:work-item:update')")
    public CommonResult<Boolean> updateOptionEnabled(@RequestParam("id") @NotNull Long id,
                                                     @RequestParam("enabled") @NotNull Boolean enabled) {
        fieldService.updateOptionEnabled(id, enabled);
        return success(true);
    }

}
