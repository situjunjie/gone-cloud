package cn.iocoder.yudao.module.project.controller.admin.workitem;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemGroupCountReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemGroupCountRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemSaveReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemUpdateFieldReqVO;
import cn.iocoder.yudao.module.project.service.workitem.ProjectWorkItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.dromara.core.trans.anno.TransMethodResult;
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

@Tag(name = "管理后台 - 项目工作项")
@RestController
@RequestMapping("/project/work-item")
@Validated
public class ProjectWorkItemController {

    @Resource
    private ProjectWorkItemService workItemService;

    @PostMapping("/create")
    @Operation(summary = "创建工作项")
    @PreAuthorize("@ss.hasPermission('project:work-item:create')")
    public CommonResult<Long> createWorkItem(@Valid @RequestBody ProjectWorkItemSaveReqVO createReqVO) {
        return success(workItemService.createWorkItem(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新工作项")
    @PreAuthorize("@ss.hasPermission('project:work-item:update')")
    public CommonResult<Boolean> updateWorkItem(@Valid @RequestBody ProjectWorkItemSaveReqVO updateReqVO) {
        workItemService.updateWorkItem(updateReqVO);
        return success(true);
    }

    @PutMapping("/update-field")
    @Operation(summary = "更新工作项字段")
    @PreAuthorize("@ss.hasPermission('project:work-item:update')")
    public CommonResult<Boolean> updateWorkItemField(@Valid @RequestBody ProjectWorkItemUpdateFieldReqVO updateReqVO) {
        workItemService.updateWorkItemField(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除工作项")
    @Parameter(name = "id", description = "工作项编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('project:work-item:delete')")
    public CommonResult<Boolean> deleteWorkItem(@RequestParam("id") Long id) {
        workItemService.deleteWorkItem(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得工作项")
    @Parameter(name = "id", description = "工作项编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('project:work-item:query')")
    @TransMethodResult
    public CommonResult<ProjectWorkItemRespVO> getWorkItem(@RequestParam("id") Long id) {
        return success(workItemService.getWorkItem(id));
    }

    @PostMapping("/page")
    @Operation(summary = "获得工作项分页")
    @PreAuthorize("@ss.hasPermission('project:work-item:query')")
    @TransMethodResult
    public CommonResult<PageResult<ProjectWorkItemRespVO>> getWorkItemPage(@Valid @RequestBody ProjectWorkItemPageReqVO pageReqVO) {
        return success(workItemService.getWorkItemPage(pageReqVO));
    }

    @PostMapping("/group-count")
    @Operation(summary = "按单一字段分组统计工作项数量")
    @PreAuthorize("@ss.hasPermission('project:work-item:query')")
    public CommonResult<ProjectWorkItemGroupCountRespVO> getWorkItemGroupCount(
            @Valid @RequestBody ProjectWorkItemGroupCountReqVO reqVO) {
        return success(workItemService.getWorkItemGroupCount(reqVO));
    }

}
