package cn.iocoder.yudao.module.devops.dal.dataobject.variablegroup;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("dev_variable_group")
@KeySequence("dev_variable_group_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class VariableGroupDO extends TenantBaseDO {
    @TableId private Long id;
    private String name;
    private String scope;
    private Long appId;
    private String variables;
    private Integer status;
    private String remark;
}
