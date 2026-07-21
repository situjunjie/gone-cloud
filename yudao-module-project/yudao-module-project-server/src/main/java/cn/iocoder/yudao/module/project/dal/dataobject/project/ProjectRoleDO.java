package cn.iocoder.yudao.module.project.dal.dataobject.project;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("proj_project_role")
@KeySequence("proj_project_role_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProjectRoleDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String scope;
    private Long projectId;
    private String code;
    private String name;
    private String builtinType;
    private Integer sort;
    private String remark;

}
