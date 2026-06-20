package cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.devops.enums.ArtifactRepositoryFormatEnum;
import cn.iocoder.yudao.module.devops.enums.ArtifactRepositoryTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * DevOps 制品仓库配置 DO。
 */
@TableName("dev_artifact_repository")
@KeySequence("dev_artifact_repository_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ArtifactRepositoryDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 制品仓库实例编号。
     */
    private Long registryId;
    /**
     * Nexus 仓库名称。
     */
    private String repositoryName;
    /**
     * 仓库格式。枚举 {@link ArtifactRepositoryFormatEnum}。
     */
    private String format;
    /**
     * 仓库类型。枚举 {@link ArtifactRepositoryTypeEnum}。
     */
    private String repositoryType;
    /**
     * 仓库 URL。
     */
    private String url;
    /**
     * Nexus online 状态。
     */
    private Boolean online;
    /**
     * 状态。枚举 {@link CommonStatusEnum}。
     */
    private Integer status;
    /**
     * 最近同步时间。
     */
    private LocalDateTime lastSyncTime;
    /**
     * 备注。
     */
    private String remark;

}
