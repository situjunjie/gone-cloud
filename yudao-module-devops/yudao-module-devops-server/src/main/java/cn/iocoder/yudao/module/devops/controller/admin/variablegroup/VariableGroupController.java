package cn.iocoder.yudao.module.devops.controller.admin.variablegroup;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.devops.controller.admin.variablegroup.vo.*;
import cn.iocoder.yudao.module.devops.convert.variablegroup.VariableGroupConvert;
import cn.iocoder.yudao.module.devops.service.variablegroup.VariableGroupService;
import jakarta.annotation.Resource; import jakarta.validation.Valid; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.validation.annotation.Validated; import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/devops/variable-group") @Validated
public class VariableGroupController {
 @Resource private VariableGroupService service;
 @PostMapping("/create") @PreAuthorize("@ss.hasPermission('devops:variable-group:create')") public CommonResult<Long> create(@Valid @RequestBody VariableGroupSaveReqVO r){return success(service.createVariableGroup(r));}
 @PutMapping("/update") @PreAuthorize("@ss.hasPermission('devops:variable-group:update')") public CommonResult<Boolean> update(@Valid @RequestBody VariableGroupSaveReqVO r){service.updateVariableGroup(r);return success(true);}
 @DeleteMapping("/delete") @PreAuthorize("@ss.hasPermission('devops:variable-group:delete')") public CommonResult<Boolean> delete(@RequestParam Long id){service.deleteVariableGroup(id);return success(true);}
 @GetMapping("/get") @PreAuthorize("@ss.hasPermission('devops:variable-group:query')") public CommonResult<VariableGroupRespVO> get(@RequestParam Long id){return success(VariableGroupConvert.INSTANCE.convert(service.getVariableGroup(id)));}
 @GetMapping("/page") @PreAuthorize("@ss.hasPermission('devops:variable-group:query')") public CommonResult<PageResult<VariableGroupRespVO>> page(@Valid VariableGroupPageReqVO r){return success(VariableGroupConvert.INSTANCE.convertPage(service.getVariableGroupPage(r)));}
 @GetMapping("/simple-list") @PreAuthorize("@ss.hasPermission('devops:variable-group:query')") public CommonResult<java.util.List<VariableGroupSimpleRespVO>> simpleList(@RequestParam Long appId){return success(service.getVariableGroupSimpleList(appId));}
}
