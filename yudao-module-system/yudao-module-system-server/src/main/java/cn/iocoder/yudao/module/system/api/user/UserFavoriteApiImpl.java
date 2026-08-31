package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.user.dto.UserFavoriteRespDTO;
import cn.iocoder.yudao.module.system.service.user.UserFavoriteService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
public class UserFavoriteApiImpl implements UserFavoriteApi {

    @Resource
    private UserFavoriteService userFavoriteService;

    @Override
    public CommonResult<List<UserFavoriteRespDTO>> getUserFavoriteList(String bizType) {
        Long userId = cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
        Long tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
        return success(BeanUtils.toBean(userFavoriteService.getUserFavoriteList(userId, tenantId, bizType),
                UserFavoriteRespDTO.class));
    }
}
