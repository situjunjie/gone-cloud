package cn.iocoder.yudao.module.devops.controller.admin.pipeline;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineDefinitionRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineDefinitionVersionRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineNodeTypeRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelinePublishReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineSaveDraftReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidateReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationRespVO;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineDefinitionService;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - DevOps 流水线")
@RestController
@RequestMapping("/devops/pipeline")
@Validated
public class PipelineController {

    @Resource
    private PipelineNodeRegistryService pipelineNodeRegistryService;
    @Resource
    private PipelineDefinitionService pipelineDefinitionService;

    @GetMapping("/node-types")
    @Operation(summary = "获得流水线节点类型")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:query')")
    public CommonResult<List<PipelineNodeTypeRespVO>> getNodeTypes() {
        return success(pipelineNodeRegistryService.getNodeTypes());
    }

    @GetMapping("/configurable-node-types")
    @Operation(summary = "获得可配置的流水线节点类型")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:query')")
    public CommonResult<List<PipelineNodeTypeRespVO>> getConfigurableNodeTypes() {
        return success(pipelineNodeRegistryService.getConfigurableNodeTypes());
    }

    @GetMapping("/get-by-application-env")
    @Operation(summary = "获得应用环境流水线")
    @Parameter(name = "applicationEnvId", description = "应用环境关系编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:query')")
    public CommonResult<PipelineDefinitionRespVO> getByApplicationEnv(@RequestParam("applicationEnvId") Long applicationEnvId) {
        return success(pipelineDefinitionService.getByApplicationEnvId(applicationEnvId));
    }

    @PostMapping("/save-draft")
    @Operation(summary = "保存流水线草稿")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:update')")
    public CommonResult<Long> saveDraft(@Valid @RequestBody PipelineSaveDraftReqVO reqVO) {
        return success(pipelineDefinitionService.saveDraft(reqVO));
    }

    @PostMapping("/validate")
    @Operation(summary = "校验流水线 DSL")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:query')")
    public CommonResult<PipelineValidationRespVO> validate(@Valid @RequestBody PipelineValidateReqVO reqVO) {
        return success(pipelineDefinitionService.validate(reqVO));
    }

    @PostMapping("/publish")
    @Operation(summary = "发布流水线版本")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:publish')")
    public CommonResult<Long> publish(@Valid @RequestBody PipelinePublishReqVO reqVO) {
        return success(pipelineDefinitionService.publish(reqVO, getLoginUserId()));
    }

    @GetMapping("/version/list")
    @Operation(summary = "获得流水线版本列表")
    @Parameter(name = "definitionId", description = "流水线定义编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:query')")
    public CommonResult<List<PipelineDefinitionVersionRespVO>> getVersionList(@RequestParam("definitionId") Long definitionId) {
        return success(pipelineDefinitionService.getVersionList(definitionId));
    }

}
