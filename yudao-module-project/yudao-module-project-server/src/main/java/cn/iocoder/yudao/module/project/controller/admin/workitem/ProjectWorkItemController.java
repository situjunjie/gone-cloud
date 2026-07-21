package cn.iocoder.yudao.module.project.controller.admin.workitem;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemGroupCountReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemGroupCountRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemSaveReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemUpdateFieldReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldRespVO;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemCategoryEnum;
import cn.iocoder.yudao.module.project.service.workitem.ProjectWorkItemService;
import cn.iocoder.yudao.module.project.service.workitem.WorkItemFieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 项目工作项")
@RestController
@RequestMapping("/project/work-item")
@Validated
public class ProjectWorkItemController {

    @Resource
    private ProjectWorkItemService workItemService;
    @Resource
    private WorkItemFieldService workItemFieldService;

    @PostMapping("/create")
    @Operation(summary = "创建工作项")
    @PreAuthorize("@ss.hasPermission('project:work-item:create') and "
            + "@projectSecurity.hasPermission(#createReqVO.projectId, 'WORK_ITEM_CREATE')")
    public CommonResult<Long> createWorkItem(@Valid @RequestBody ProjectWorkItemSaveReqVO createReqVO) {
        return success(workItemService.createWorkItem(createReqVO));
    }

    @GetMapping("/schema")
    @Operation(summary = "获得项目工作项自定义字段 Schema")
    @PreAuthorize("@ss.hasPermission('project:work-item:query') and "
            + "@projectSecurity.hasPermission(#projectId, 'WORK_ITEM_FIELD_QUERY')")
    public CommonResult<List<WorkItemFieldRespVO>> getWorkItemSchema(
            @Parameter(description = "项目编号", required = true, example = "1024")
            @RequestParam("projectId") @NotNull Long projectId,
            @Parameter(description = "工作项分类，参见 project_work_item_category", example = "REQUIREMENT")
            @RequestParam(value = "category", required = false)
            @InEnum(value = ProjectWorkItemCategoryEnum.class, message = "工作项分类不正确") String category) {
        return success(workItemFieldService.getFieldSchema(projectId, category));
    }

    @PutMapping("/update")
    @Operation(summary = "更新工作项")
    @PreAuthorize("@ss.hasPermission('project:work-item:update') and "
            + "@projectSecurity.hasWorkItemPermission(#updateReqVO.id, 'WORK_ITEM_UPDATE')")
    public CommonResult<Boolean> updateWorkItem(@Valid @RequestBody ProjectWorkItemSaveReqVO updateReqVO) {
        workItemService.updateWorkItem(updateReqVO);
        return success(true);
    }

    @PutMapping("/update-field")
    @Operation(summary = "更新工作项字段")
    @PreAuthorize("@ss.hasPermission('project:work-item:update') and "
            + "@projectSecurity.hasWorkItemPermission(#updateReqVO.id, 'WORK_ITEM_UPDATE')")
    public CommonResult<Boolean> updateWorkItemField(@Valid @RequestBody ProjectWorkItemUpdateFieldReqVO updateReqVO) {
        workItemService.updateWorkItemField(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除工作项")
    @Parameter(name = "id", description = "工作项编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('project:work-item:delete') and "
            + "@projectSecurity.hasWorkItemPermission(#id, 'WORK_ITEM_DELETE')")
    public CommonResult<Boolean> deleteWorkItem(@RequestParam("id") Long id) {
        workItemService.deleteWorkItem(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得工作项")
    @Parameter(name = "id", description = "工作项编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('project:work-item:query') and "
            + "@projectSecurity.hasWorkItemPermission(#id, 'WORK_ITEM_QUERY')")
    @TransMethodResult
    public CommonResult<ProjectWorkItemRespVO> getWorkItem(@RequestParam("id") Long id) {
        return success(workItemService.getWorkItem(id));
    }

    @PostMapping("/page")
    @Operation(summary = "获得工作项分页")
    @PreAuthorize("@ss.hasPermission('project:work-item:query') and "
            + "(#pageReqVO.projectId == null or @projectSecurity.hasPermission(#pageReqVO.projectId, 'WORK_ITEM_QUERY'))")
    @TransMethodResult
    public CommonResult<PageResult<ProjectWorkItemRespVO>> getWorkItemPage(@Valid @RequestBody ProjectWorkItemPageReqVO pageReqVO) {
        return success(workItemService.getWorkItemPage(pageReqVO));
    }

    @PostMapping("/group-count")
    @Operation(summary = "按单一字段分组统计工作项数量")
    @PreAuthorize("@ss.hasPermission('project:work-item:query') and "
            + "(#reqVO.projectId == null or @projectSecurity.hasPermission(#reqVO.projectId, 'WORK_ITEM_QUERY'))")
    public CommonResult<ProjectWorkItemGroupCountRespVO> getWorkItemGroupCount(
            @Valid @RequestBody ProjectWorkItemGroupCountReqVO reqVO) {
        return success(workItemService.getWorkItemGroupCount(reqVO));
    }

}
