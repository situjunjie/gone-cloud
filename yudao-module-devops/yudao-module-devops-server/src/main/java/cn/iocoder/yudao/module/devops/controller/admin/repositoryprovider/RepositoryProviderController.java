package cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo.RepositoryProviderPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo.RepositoryProviderProjectRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo.RepositoryProviderRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo.RepositoryProviderSaveReqVO;
import cn.iocoder.yudao.module.devops.convert.repositoryprovider.RepositoryProviderConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;
import cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService;
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

@Tag(name = "管理后台 - DevOps 代码源")
@RestController
@RequestMapping("/devops/repository-provider")
@Validated
public class RepositoryProviderController {

    @Resource
    private RepositoryProviderService repositoryProviderService;

    @PostMapping("/create")
    @Operation(summary = "创建代码源")
    @PreAuthorize("@ss.hasPermission('devops:repository-provider:create')")
    public CommonResult<Long> createRepositoryProvider(@Valid @RequestBody RepositoryProviderSaveReqVO createReqVO) {
        return success(repositoryProviderService.createRepositoryProvider(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新代码源")
    @PreAuthorize("@ss.hasPermission('devops:repository-provider:update')")
    public CommonResult<Boolean> updateRepositoryProvider(@Valid @RequestBody RepositoryProviderSaveReqVO updateReqVO) {
        repositoryProviderService.updateRepositoryProvider(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除代码源")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:repository-provider:delete')")
    public CommonResult<Boolean> deleteRepositoryProvider(@RequestParam("id") Long id) {
        repositoryProviderService.deleteRepositoryProvider(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得代码源")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:repository-provider:query')")
    public CommonResult<RepositoryProviderRespVO> getRepositoryProvider(@RequestParam("id") Long id) {
        return success(RepositoryProviderConvert.INSTANCE.convert(repositoryProviderService.getRepositoryProvider(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "获得代码源分页")
    @PreAuthorize("@ss.hasPermission('devops:repository-provider:query')")
    public CommonResult<PageResult<RepositoryProviderRespVO>> getRepositoryProviderPage(
            @Valid RepositoryProviderPageReqVO pageReqVO) {
        PageResult<RepositoryProviderDO> pageResult = repositoryProviderService.getRepositoryProviderPage(pageReqVO);
        return success(RepositoryProviderConvert.INSTANCE.convertPage(pageResult));
    }

    @PostMapping("/check")
    @Operation(summary = "检测代码源连接")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:repository-provider:update')")
    public CommonResult<Boolean> checkRepositoryProvider(@RequestParam("id") Long id) {
        repositoryProviderService.checkRepositoryProvider(id);
        return success(true);
    }

    @GetMapping("/projects")
    @Operation(summary = "获得 GitLab 项目列表")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:repository-provider:query')")
    public CommonResult<List<RepositoryProviderProjectRespVO>> getRepositoryProviderProjects(
            @RequestParam("id") Long id) {
        return success(repositoryProviderService.getRepositoryProviderProjects(id));
    }

}
