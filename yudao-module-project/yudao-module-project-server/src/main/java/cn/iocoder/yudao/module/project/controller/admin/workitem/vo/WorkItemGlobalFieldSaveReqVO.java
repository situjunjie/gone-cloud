package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 全局工作项自定义字段保存 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkItemGlobalFieldSaveReqVO extends WorkItemFieldBaseReqVO {

    private Long id;

}
