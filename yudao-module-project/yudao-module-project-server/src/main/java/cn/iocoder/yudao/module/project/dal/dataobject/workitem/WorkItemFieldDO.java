package cn.iocoder.yudao.module.project.dal.dataobject.workitem;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("proj_work_item_field")
@KeySequence("proj_work_item_field_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WorkItemFieldDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long projectId;
    private String code;
    private String name;
    private String fieldType;
    private String applicableCategory;
    private Boolean required;
    private Boolean enabled;
    private Integer sort;
    private String configJson;

}
