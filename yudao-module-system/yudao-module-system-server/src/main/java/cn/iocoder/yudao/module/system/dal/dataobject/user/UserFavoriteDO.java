package cn.iocoder.yudao.module.system.dal.dataobject.user;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 管理后台用户收藏关系 DO
 */
@TableName("system_user_favorite")
@KeySequence("system_user_favorite_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFavoriteDO extends TenantBaseDO {

    /**
     * 收藏编号
     */
    @TableId
    private Long id;
    /**
     * 用户编号
     *
     * 关联 {@link AdminUserDO#getId()}
     */
    private Long userId;
    /**
     * 业务类型
     */
    private String bizType;
    /**
     * 业务对象编号
     */
    private Long bizId;

}
