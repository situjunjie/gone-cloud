package cn.iocoder.yudao.module.project.dal.dataobject.workitem;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("proj_work_item_field_option")
@KeySequence("proj_work_item_field_option_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WorkItemFieldOptionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long fieldId;
    private String code;
    private String name;
    private String color;
    private Boolean enabled;
    private Integer sort;

}
