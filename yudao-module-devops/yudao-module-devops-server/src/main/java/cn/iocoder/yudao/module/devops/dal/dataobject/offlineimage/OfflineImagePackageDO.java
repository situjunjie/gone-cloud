package cn.iocoder.yudao.module.devops.dal.dataobject.offlineimage;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * DevOps 离线镜像包 DO。
 */
@TableName("dev_offline_image_package")
@KeySequence("dev_offline_image_package_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class OfflineImagePackageDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 流水线运行编号。
     */
    private Long pipelineRunId;
    /**
     * 镜像名称。
     */
    private String imageName;
    /**
     * 镜像标签。
     */
    private String imageTag;
    /**
     * 镜像摘要（SHA256）。
     */
    private String imageDigest;
    /**
     * 目标架构。
     */
    private String architecture;
    /**
     * 离线包 OSS 访问地址。
     */
    private String ossUrl;
    /**
     * 包大小（字节）。
     */
    private Long packageSize;
    /**
     * 状态（0 打包中 1 就绪 2 失败）。
     */
    private Integer status;
    /**
     * 错误信息。
     */
    private String errorMessage;

}
