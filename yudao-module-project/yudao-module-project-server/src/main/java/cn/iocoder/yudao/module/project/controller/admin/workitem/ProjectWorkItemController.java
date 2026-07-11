package cn.iocoder.yudao.module.project.controller.admin.workitem;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemSaveReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemUpdateFieldReqVO;
import cn.iocoder.yudao.module.project.convert.workitem.ProjectWorkItemConvert;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.service.workitem.ProjectWorkItemService;
import cn.iocoder.yudao.module.project.service.workitem.WorkItemCustomFieldValueService;
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

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 项目工作项")
@RestController
@RequestMapping("/project/work-item")
@Validated
public class ProjectWorkItemController {

    @Resource
    private ProjectWorkItemService workItemService;
    @Resource
    private WorkItemCustomFieldValueService customFieldValueService;

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
        ProjectWorkItemRespVO respVO = ProjectWorkItemConvert.INSTANCE.convert(workItemService.getWorkItem(id));
        respVO.setCustomFields(customFieldValueService.getValueMap(List.of(id)).getOrDefault(id, List.of()));
        return success(respVO);
    }

    @PostMapping("/page")
    @Operation(summary = "获得工作项分页")
    @PreAuthorize("@ss.hasPermission('project:work-item:query')")
    @TransMethodResult
    public CommonResult<PageResult<ProjectWorkItemRespVO>> getWorkItemPage(@Valid @RequestBody ProjectWorkItemPageReqVO pageReqVO) {
        PageResult<ProjectWorkItemDO> pageResult = workItemService.getWorkItemPage(pageReqVO);
        PageResult<ProjectWorkItemRespVO> respPage = ProjectWorkItemConvert.INSTANCE.convertPage(pageResult);
        Map<Long, List<cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemCustomFieldValueRespVO>> valueMap =
                customFieldValueService.getValueMap(pageResult.getList().stream().map(ProjectWorkItemDO::getId).toList());
        respPage.getList().forEach(item -> item.setCustomFields(valueMap.getOrDefault(item.getId(), List.of())));
        return success(respPage);
    }

}
