package cn.iocoder.yudao.module.project.dal.dataobject.workitem;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("proj_work_item_field_value")
@KeySequence("proj_work_item_field_value_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WorkItemFieldValueDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long workItemId;
    private Long fieldId;
    private String textValue;
    private BigDecimal numberValue;
    private LocalDateTime datetimeValue;
    private Boolean booleanValue;
    private Long userValue;

}
