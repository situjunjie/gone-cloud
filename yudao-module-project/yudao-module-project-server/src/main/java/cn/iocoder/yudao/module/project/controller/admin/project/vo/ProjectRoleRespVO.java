package cn.iocoder.yudao.module.project.controller.admin.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Schema(description = "管理后台 - 项目角色 Response VO")
@Data
public class ProjectRoleRespVO {

    private Long id;
    private String scope;
    private Long projectId;
    private String code;
    private String name;
    private String builtinType;
    private Integer sort;
    private String remark;
    private Set<String> permissionCodes;
    private LocalDateTime createTime;

}
