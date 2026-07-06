package cn.iocoder.yudao.module.project.dal.dataobject.project;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.project.enums.ProjectStatusEnum;
import cn.iocoder.yudao.module.project.enums.ProjectVisibilityEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 项目 DO。
 */
@TableName("proj_project")
@KeySequence("proj_project_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProjectDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 项目封面地址。
     */
    private String coverUrl;
    /**
     * 项目名称。
     */
    private String name;
    /**
     * 公开性。枚举 {@link ProjectVisibilityEnum}。
     */
    private String visibility;
    /**
     * 项目编号展示值。
     */
    private String code;
    /**
     * 项目描述。
     */
    private String description;
    /**
     * 项目公告。
     */
    private String announcement;
    /**
     * 负责人用户编号。
     */
    private Long ownerUserId;
    /**
     * 状态。枚举 {@link ProjectStatusEnum}。
     */
    private String status;
    /**
     * 排序。
     */
    private Integer sort;

}
