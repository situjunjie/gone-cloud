package cn.iocoder.yudao.module.project.dal.dataobject.project;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 项目成员 DO。
 */
@TableName("proj_project_member")
@KeySequence("proj_project_member_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProjectMemberDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 项目编号。
     */
    private Long projectId;
    /**
     * 用户编号。
     */
    private Long userId;
}
