package cn.iocoder.yudao.module.project.controller.admin.workitem;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemAssignReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemSaveReqVO;
import cn.iocoder.yudao.module.project.convert.workitem.ProjectWorkItemConvert;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
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
        return success(ProjectWorkItemConvert.INSTANCE.convert(workItemService.getWorkItem(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "获得工作项分页")
    @PreAuthorize("@ss.hasPermission('project:work-item:query')")
    @TransMethodResult
    public CommonResult<PageResult<ProjectWorkItemRespVO>> getWorkItemPage(@Valid ProjectWorkItemPageReqVO pageReqVO) {
        PageResult<ProjectWorkItemDO> pageResult = workItemService.getWorkItemPage(pageReqVO);
        return success(ProjectWorkItemConvert.INSTANCE.convertPage(pageResult));
    }

    @PutMapping("/assign")
    @Operation(summary = "指派工作项负责人")
    @PreAuthorize("@ss.hasPermission('project:work-item:assign')")
    public CommonResult<Boolean> assignWorkItem(@Valid @RequestBody ProjectWorkItemAssignReqVO assignReqVO) {
        workItemService.assignWorkItem(assignReqVO);
        return success(true);
    }

}
