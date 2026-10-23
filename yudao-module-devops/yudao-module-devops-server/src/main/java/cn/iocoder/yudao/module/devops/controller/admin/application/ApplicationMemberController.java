package cn.iocoder.yudao.module.devops.controller.admin.application;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationMemberAddReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationMemberRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationMemberTransferOwnerReqVO;
import cn.iocoder.yudao.module.devops.service.application.ApplicationMemberService;
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

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - DevOps 应用成员")
@RestController
@RequestMapping("/devops/application/member")
@Validated
public class ApplicationMemberController {

    @Resource
    private ApplicationMemberService applicationMemberService;

    @GetMapping("/list")
    @Operation(summary = "获得应用成员列表")
    @Parameter(name = "appId", description = "应用编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('devops:application:query')")
    @TransMethodResult
    public CommonResult<List<ApplicationMemberRespVO>> getApplicationMemberList(@RequestParam("appId") Long appId) {
        return success(applicationMemberService.getApplicationMemberList(appId, getLoginUserId()));
    }

    @PostMapping("/add")
    @Operation(summary = "添加应用参与者")
    @PreAuthorize("@ss.hasPermission('devops:application:update')")
    public CommonResult<Boolean> addApplicationMembers(@Valid @RequestBody ApplicationMemberAddReqVO reqVO) {
        applicationMemberService.addApplicationMembers(reqVO, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/remove")
    @Operation(summary = "移除应用参与者")
    @Parameter(name = "appId", description = "应用编号", required = true, example = "1024")
    @Parameter(name = "userId", description = "用户编号", required = true, example = "2")
    @PreAuthorize("@ss.hasPermission('devops:application:update')")
    public CommonResult<Boolean> removeApplicationMember(@RequestParam("appId") Long appId,
                                                         @RequestParam("userId") Long userId) {
        applicationMemberService.removeApplicationMember(appId, userId, getLoginUserId());
        return success(true);
    }

    @PutMapping("/transfer-owner")
    @Operation(summary = "转移应用拥有者")
    @PreAuthorize("@ss.hasPermission('devops:application:update')")
    public CommonResult<Boolean> transferApplicationOwner(
            @Valid @RequestBody ApplicationMemberTransferOwnerReqVO reqVO) {
        applicationMemberService.transferApplicationOwner(reqVO, getLoginUserId());
        return success(true);
    }

}
