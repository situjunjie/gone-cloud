package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "管理后台 - 项目工作项单字段分组统计 Response VO")
@Data
public class ProjectWorkItemGroupCountRespVO {

    @Schema(description = "分组字段编码", example = "status")
    private String groupField;

    @Schema(description = "分组统计结果")
    private List<Group> groups;

    @Schema(description = "工作项分组统计项")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Group {

        @Schema(description = "分组字段原始值", example = "PENDING")
        private Object value;

        @Schema(description = "工作项数量", example = "12")
        private Long count;

    }

}
