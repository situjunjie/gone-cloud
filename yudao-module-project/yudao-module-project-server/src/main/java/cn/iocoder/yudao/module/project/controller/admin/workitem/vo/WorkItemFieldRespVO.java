package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 工作项自定义字段 Response VO")
@Data
public class WorkItemFieldRespVO {

    private Long id;
    private String scope;
    private Long projectId;
    private String code;
    private String name;
    private String fieldType;
    private String applicableCategory;
    private Boolean required;
    private Boolean enabled;
    private Integer sort;
    private String configJson;
    private LocalDateTime createTime;
    private List<Option> options;

    @Data
    public static class Option {
        private Long id;
        private Long fieldId;
        private String code;
        private String name;
        private String color;
        private Boolean enabled;
        private Integer sort;
    }

}
