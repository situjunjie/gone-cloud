package cn.iocoder.yudao.module.devops.dal.dataobject.applicationorchestration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * DevOps 应用编排 DO。
 */
@TableName("dev_application_orchestration")
@KeySequence("dev_application_orchestration_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ApplicationOrchestrationDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long appId;
    private String orchestrationType;
    private String schemaVersion;
    private String componentJson;
    private String canvasJson;
    private String validationResultJson;

}
