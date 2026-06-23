package cn.iocoder.yudao.module.devops.controller.admin.pipelinerun;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.CodeMergeConflictDetailRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.CodeMergeConflictResolutionReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.CodeMergeConflictRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogRespVO;
import cn.iocoder.yudao.module.devops.service.pipeline.approval.PipelineApprovalService;
import cn.iocoder.yudao.module.devops.service.pipeline.approval.PipelineApprovalStatusHandleResult;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionEngine;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineRunLogLineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - DevOps 流水线运行")
@RestController
@RequestMapping("/devops/pipeline-run")
@Validated
public class PipelineRunController {

    @Resource
    private PipelineExecutionService pipelineExecutionService;
    @Resource
    private PipelineApprovalService pipelineApprovalService;
    @Resource
    private PipelineExecutionEngine pipelineExecutionEngine;
    @Resource
    private PipelineRunLogLineService pipelineRunLogLineService;

    @GetMapping("/{runId}/logs")
    @Operation(summary = "获得流水线运行日志")
    @Parameter(name = "runId", description = "流水线运行编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:query')")
    public CommonResult<List<PipelineRunLogRespVO>> getRunLogs(@PathVariable("runId") Long runId) {
        return success(pipelineExecutionService.getRunLogs(runId));
    }

    @GetMapping("/{runId}/log-lines")
    @Operation(summary = "获得流水线运行行级日志")
    @Parameter(name = "runId", description = "流水线运行编号", required = true, example = "1024")
    @Parameter(name = "stepId", description = "步骤编号，可空")
    @Parameter(name = "afterId", description = "日志行游标编号，仅返回该游标后的行", example = "100")
    @Parameter(name = "limit", description = "返回条数，最大 500", example = "200")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:query')")
    public CommonResult<List<PipelineRunLogLineRespVO>> getRunLogLines(@PathVariable("runId") Long runId,
                                                                       @RequestParam(value = "stepId", required = false)
                                                                       String stepId,
                                                                       @RequestParam(value = "afterId", required = false)
                                                                       Long afterId,
                                                                       @RequestParam(value = "limit", required = false)
                                                                       Integer limit) {
        return success(pipelineRunLogLineService.getLogLines(runId, stepId, afterId, limit));
    }

    @GetMapping(value = "/{runId}/log-lines/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式查看流水线运行行级日志")
    @Parameter(name = "runId", description = "流水线运行编号", required = true, example = "1024")
    @Parameter(name = "stepId", description = "步骤编号，可空")
    @Parameter(name = "afterId", description = "日志行游标编号，仅推送该游标后的行", example = "100")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:query')")
    public SseEmitter streamRunLogLines(@PathVariable("runId") Long runId,
                                        @RequestParam(value = "stepId", required = false) String stepId,
                                        @RequestParam(value = "afterId", required = false) Long afterId) {
        return pipelineRunLogLineService.streamLogLines(runId, stepId, afterId);
    }

    @GetMapping("/{runId}/code-merge/conflicts")
    @Operation(summary = "获得代码合并冲突列表")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:query')")
    public CommonResult<List<CodeMergeConflictRespVO>> getCodeMergeConflicts(@PathVariable("runId") Long runId) {
        return success(pipelineExecutionService.getCodeMergeConflicts(runId));
    }

    @GetMapping("/{runId}/code-merge/conflict-detail")
    @Operation(summary = "获得代码合并冲突详情")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:query')")
    public CommonResult<CodeMergeConflictDetailRespVO> getCodeMergeConflictDetail(
            @PathVariable("runId") Long runId, @RequestParam("filePath") String filePath) {
        return success(pipelineExecutionService.getCodeMergeConflictDetail(runId, filePath));
    }

    @PutMapping("/{runId}/code-merge/conflict-resolution")
    @Operation(summary = "保存代码合并冲突解决")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:update')")
    public CommonResult<Boolean> saveCodeMergeConflictResolution(@PathVariable("runId") Long runId,
                                                                 @Valid @RequestBody CodeMergeConflictResolutionReqVO reqVO) {
        pipelineExecutionService.saveCodeMergeConflictResolution(runId, reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/{runId}/code-merge/continue")
    @Operation(summary = "继续代码合并")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:update')")
    public CommonResult<Boolean> continueCodeMerge(@PathVariable("runId") Long runId) {
        pipelineExecutionService.continueCodeMerge(runId, getLoginUserId());
        return success(true);
    }

    @PostMapping("/{runId}/code-merge/retry-current-change")
    @Operation(summary = "刷新并重试当前冲突变更")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:update')")
    public CommonResult<Boolean> retryCurrentCodeMergeChange(@PathVariable("runId") Long runId) {
        pipelineExecutionService.retryCurrentCodeMergeChange(runId, getLoginUserId());
        return success(true);
    }

    @PostMapping("/{runId}/cancel")
    @Operation(summary = "取消流水线运行")
    @PreAuthorize("@ss.hasPermission('devops:pipeline:update')")
    public CommonResult<Boolean> cancelRun(@PathVariable("runId") Long runId) {
        pipelineExecutionService.cancelRun(runId, getLoginUserId());
        return success(true);
    }

    @PostMapping("/bpm/approval-status")
    @Operation(summary = "BPM 审批结果回调")
    @TenantIgnore
    public CommonResult<Boolean> handleBpmApprovalStatus(@Valid @RequestBody BpmProcessInstanceStatusEvent event) {
        PipelineApprovalStatusHandleResult result = pipelineApprovalService.handleProcessInstanceStatus(event);
        if (Boolean.TRUE.equals(result.getHandled()) && Boolean.TRUE.equals(result.getApproved())) {
            // 审批通过后重入引擎执行(引擎幂等跳过已完成节点，继续后续节点)
            pipelineExecutionEngine.execute(result.getRun(), null);
        }
        return success(Boolean.TRUE.equals(result.getHandled()));
    }

}
