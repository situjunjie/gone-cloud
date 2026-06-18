package cn.iocoder.yudao.module.devops.dal.dataobject.pipeline;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * DevOps 流水线定义版本 DO。
 */
@TableName("dev_pipeline_definition_version")
@KeySequence("dev_pipeline_definition_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PipelineDefinitionVersionDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 流水线定义编号。
     */
    private Long definitionId;
    /**
     * 版本号。
     */
    private Integer versionNo;
    /**
     * 版本名称。
     */
    private String versionName;
    /**
     * 版本状态。字典：dev_pipeline_definition_version_status。
     */
    private Integer versionStatus;
    /**
     * 画布 JSON。
     */
    private String diagramJson;
    /**
     * 后端执行 DSL JSON。
     */
    private String specJson;
    /**
     * 节点 schema 版本。
     */
    private String nodeSchemaVersion;
    /**
     * 回退来源版本编号。
     */
    private Long rollbackFromVersionId;
    /**
     * 回退来源版本号。
     */
    private Integer rollbackFromVersionNo;
    /**
     * 回退发生时的当前版本编号。
     */
    private Long basedOnCurrentVersionId;
    /**
     * 回退发生时的当前版本号。
     */
    private Integer basedOnCurrentVersionNo;
    /**
     * 回退原因。
     */
    private String rollbackReason;
    /**
     * 校验结果 JSON。
     */
    private String validationResultJson;
    /**
     * 发布时间。
     */
    private LocalDateTime publishedAt;
    /**
     * 发布人用户编号。
     */
    private Long publishedBy;

}
