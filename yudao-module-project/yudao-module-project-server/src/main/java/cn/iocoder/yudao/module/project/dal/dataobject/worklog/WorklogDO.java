package cn.iocoder.yudao.module.project.dal.dataobject.worklog;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("proj_worklog")
@KeySequence("proj_worklog_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WorklogDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long projectId;
    private Long workItemId;
    private String type;
    private BigDecimal hours;
    private String category;
    private String description;
    private Long ownerUserId;
    private LocalDate workDate;

}
