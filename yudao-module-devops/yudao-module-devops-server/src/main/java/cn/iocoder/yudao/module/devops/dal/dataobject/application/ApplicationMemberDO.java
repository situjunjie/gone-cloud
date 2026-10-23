package cn.iocoder.yudao.module.devops.dal.dataobject.application;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * DevOps 应用成员 DO。
 *
 * <p>成员类型不落库：{@code userId} 等于 {@code dev_application.owner_user_id} 即为拥有者，否则为参与者。
 */
@TableName("dev_application_member")
@KeySequence("dev_application_member_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ApplicationMemberDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 应用编号。
     */
    private Long appId;
    /**
     * 成员用户编号。
     */
    private Long userId;

}
