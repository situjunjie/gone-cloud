package cn.iocoder.yudao.module.devops.dal.dataobject.pipeline;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * DevOps 流水线定义 DO。
 */
@TableName("dev_pipeline_definition")
@KeySequence("dev_pipeline_definition_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PipelineDefinitionDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 流水线名称。
     */
    private String name;
    /**
     * 流水线标识。
     */
    private String definitionKey;
    /**
     * 应用编号。
     */
    private Long appId;
    /**
     * 应用环境关系编号。
     */
    private Long applicationEnvId;
    /**
     * 状态。枚举 {@link cn.iocoder.yudao.framework.common.enums.CommonStatusEnum}。
     */
    private Integer status;
    /**
     * 草稿版本编号。
     */
    private Long draftVersionId;
    /**
     * 已发布版本编号。
     */
    private Long publishedVersionId;
    /**
     * 备注。
     */
    private String remark;

}
