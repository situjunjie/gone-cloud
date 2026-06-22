package cn.iocoder.yudao.module.system.controller.admin.user;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.favorite.UserFavoriteCancelReqVO;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.favorite.UserFavoriteCreateReqVO;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.favorite.UserFavoriteRespVO;
import cn.iocoder.yudao.module.system.service.user.UserFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 用户收藏")
@RestController
@RequestMapping("/system/user-favorite")
@Validated
public class UserFavoriteController {

    @Resource
    private UserFavoriteService userFavoriteService;

    @GetMapping("/list")
    @Operation(summary = "获得当前用户指定业务类型的收藏列表")
    @Parameter(name = "bizType", description = "业务类型", required = true, example = "DEVOPS_APPLICATION")
    public CommonResult<List<UserFavoriteRespVO>> getUserFavoriteList(@RequestParam("bizType") @NotBlank String bizType) {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        return success(BeanUtils.toBean(
                userFavoriteService.getUserFavoriteList(loginUser.getId(), loginUser.getTenantId(), bizType),
                UserFavoriteRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "添加当前用户收藏")
    public CommonResult<Boolean> createUserFavorite(@Valid @RequestBody UserFavoriteCreateReqVO reqVO) {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        userFavoriteService.createUserFavorite(loginUser.getId(), loginUser.getTenantId(), reqVO);
        return success(true);
    }

    @DeleteMapping("/cancel")
    @Operation(summary = "取消当前用户收藏")
    public CommonResult<Boolean> cancelUserFavorite(@Valid @RequestBody UserFavoriteCancelReqVO reqVO) {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        userFavoriteService.cancelUserFavorite(loginUser.getId(), loginUser.getTenantId(),
                reqVO.getBizType(), reqVO.getBizId());
        return success(true);
    }

}
