package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCancelReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceStatusRespDTO;
import cn.iocoder.yudao.module.bpm.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.validation.Valid;

@FeignClient(name = ApiConstants.NAME) // TODO 芋艿：fallbackFactory =
@Tag(name = "RPC 服务 - 流程实例")
public interface BpmProcessInstanceApi {

    String PREFIX = ApiConstants.PREFIX + "/process-instance";

    @PostMapping(PREFIX + "/create")
    @Operation(summary = "创建流程实例（提供给内部），返回实例编号")
    @Parameter(name = "userId", description = "用户编号", required = true, example = "1")
    CommonResult<String> createProcessInstance(@RequestParam("userId") Long userId,
                                               @Valid @RequestBody BpmProcessInstanceCreateReqDTO reqDTO);

    @PostMapping(PREFIX + "/cancel")
    @Operation(summary = "取消流程实例（提供给内部）")
    @Parameter(name = "userId", description = "用户编号", required = true, example = "1")
    CommonResult<Boolean> cancelProcessInstance(@RequestParam("userId") Long userId,
                                                @Valid @RequestBody BpmProcessInstanceCancelReqDTO reqDTO);

    @GetMapping(PREFIX + "/status")
    @Operation(summary = "获得流程实例状态（提供给内部）")
    @Parameter(name = "id", description = "流程实例编号", required = true, example = "1024")
    CommonResult<BpmProcessInstanceStatusRespDTO> getProcessInstanceStatus(@RequestParam("id") String id);

}
