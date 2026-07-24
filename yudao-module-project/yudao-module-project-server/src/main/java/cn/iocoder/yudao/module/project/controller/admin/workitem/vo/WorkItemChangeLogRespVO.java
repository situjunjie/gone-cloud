package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 工作项修改动态 Response VO")
@Data
public class WorkItemChangeLogRespVO {

    private Long id;
    private Long userId;
    private String userName;
    private LocalDateTime createTime;
    private String subType;
    private String action;
    private List<Change> changes;

    @Data
    public static class Change {
        private String field;
        private String name;
        private String oldValue;
        private String newValue;
        private String oldDisplay;
        private String newDisplay;
    }

}
