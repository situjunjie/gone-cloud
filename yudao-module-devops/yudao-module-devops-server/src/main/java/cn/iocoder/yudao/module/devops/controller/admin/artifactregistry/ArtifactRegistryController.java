package cn.iocoder.yudao.module.devops.controller.admin.artifactregistry;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactDockerSearchReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactDockerSearchRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactMavenSearchReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactMavenSearchRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactRegistryPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactRegistryRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactRegistrySaveReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactRepositoryRespVO;
import cn.iocoder.yudao.module.devops.convert.artifactregistry.ArtifactRegistryConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry.ArtifactRegistryDO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.ArtifactRegistryService;
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

@Tag(name = "管理后台 - DevOps 制品仓库")
@RestController
@RequestMapping("/devops/artifact-registry")
@Validated
public class ArtifactRegistryController {

    @Resource
    private ArtifactRegistryService artifactRegistryService;

    @PostMapping("/create")
    @Operation(summary = "创建制品仓库")
    @PreAuthorize("@ss.hasPermission('devops:artifact-registry:create')")
    public CommonResult<Long> createArtifactRegistry(@Valid @RequestBody ArtifactRegistrySaveReqVO createReqVO) {
        return success(artifactRegistryService.createArtifactRegistry(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新制品仓库")
    @PreAuthorize("@ss.hasPermission('devops:artifact-registry:update')")
    public CommonResult<Boolean> updateArtifactRegistry(@Valid @RequestBody ArtifactRegistrySaveReqVO updateReqVO) {
        artifactRegistryService.updateArtifactRegistry(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除制品仓库")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:artifact-registry:delete')")
    public CommonResult<Boolean> deleteArtifactRegistry(@RequestParam("id") Long id) {
        artifactRegistryService.deleteArtifactRegistry(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得制品仓库")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:artifact-registry:query')")
    public CommonResult<ArtifactRegistryRespVO> getArtifactRegistry(@RequestParam("id") Long id) {
        return success(ArtifactRegistryConvert.INSTANCE.convert(artifactRegistryService.getArtifactRegistry(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "获得制品仓库分页")
    @PreAuthorize("@ss.hasPermission('devops:artifact-registry:query')")
    public CommonResult<PageResult<ArtifactRegistryRespVO>> getArtifactRegistryPage(
            @Valid ArtifactRegistryPageReqVO pageReqVO) {
        PageResult<ArtifactRegistryDO> pageResult = artifactRegistryService.getArtifactRegistryPage(pageReqVO);
        return success(ArtifactRegistryConvert.INSTANCE.convertPage(pageResult));
    }

    @PostMapping("/check")
    @Operation(summary = "检测制品仓库连接")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:artifact-registry:update')")
    public CommonResult<Boolean> checkArtifactRegistry(@RequestParam("id") Long id) {
        artifactRegistryService.checkArtifactRegistry(id);
        return success(true);
    }

    @PostMapping("/sync-repositories")
    @Operation(summary = "同步 Maven/Docker 仓库列表")
    @Parameter(name = "registryId", description = "制品仓库编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:artifact-registry:update')")
    public CommonResult<List<ArtifactRepositoryRespVO>> syncRepositories(@RequestParam("registryId") Long registryId) {
        return success(ArtifactRegistryConvert.INSTANCE.convertList(artifactRegistryService.syncRepositories(registryId)));
    }

    @GetMapping("/repositories")
    @Operation(summary = "获得 Maven/Docker 仓库列表")
    @Parameter(name = "registryId", description = "制品仓库编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:artifact-registry:query')")
    public CommonResult<List<ArtifactRepositoryRespVO>> getRepositories(@RequestParam("registryId") Long registryId,
            @RequestParam(value = "format", required = false) String format) {
        return success(ArtifactRegistryConvert.INSTANCE.convertList(artifactRegistryService.getRepositories(registryId, format)));
    }

    @GetMapping("/search")
    @Operation(summary = "搜索 Maven 制品")
    @PreAuthorize("@ss.hasPermission('devops:artifact-registry:search')")
    public CommonResult<ArtifactMavenSearchRespVO> searchMaven(@Valid ArtifactMavenSearchReqVO reqVO) {
        return success(ArtifactRegistryConvert.INSTANCE.convert(artifactRegistryService.searchMaven(reqVO)));
    }

    @GetMapping("/search-docker")
    @Operation(summary = "搜索 Docker 镜像")
    @PreAuthorize("@ss.hasPermission('devops:artifact-registry:search')")
    public CommonResult<ArtifactDockerSearchRespVO> searchDocker(@Valid ArtifactDockerSearchReqVO reqVO) {
        return success(ArtifactRegistryConvert.INSTANCE.convert(artifactRegistryService.searchDocker(reqVO)));
    }

}
