package cn.iocoder.yudao.module.system.api.user.dto;

import lombok.Data;

@Data
public class UserFavoriteRespDTO {

    private Long id;
    private Long userId;
    private String bizType;
    private Long bizId;
}
