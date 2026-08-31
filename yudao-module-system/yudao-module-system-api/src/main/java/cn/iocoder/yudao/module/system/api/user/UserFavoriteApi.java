package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.api.user.dto.UserFavoriteRespDTO;
import cn.iocoder.yudao.module.system.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = ApiConstants.NAME)
public interface UserFavoriteApi {

    String PREFIX = ApiConstants.PREFIX + "/user-favorite";

    @GetMapping(PREFIX + "/list")
    CommonResult<List<UserFavoriteRespDTO>> getUserFavoriteList(@RequestParam("bizType") String bizType);
}
