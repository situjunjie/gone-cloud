package cn.iocoder.yudao.module.project.dal.dataobject.workitem;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("proj_work_item_field_value_option")
@KeySequence("proj_work_item_field_value_option_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WorkItemFieldValueOptionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long fieldValueId;
    private Long workItemId;
    private Long fieldId;
    private Long optionId;

}
