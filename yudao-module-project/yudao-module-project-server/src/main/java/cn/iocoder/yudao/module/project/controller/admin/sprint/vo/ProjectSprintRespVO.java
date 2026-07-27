package cn.iocoder.yudao.module.project.controller.admin.sprint.vo;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.core.trans.anno.Trans;
import org.dromara.core.trans.constant.TransType;
import org.dromara.core.trans.vo.VO;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - 项目迭代 Response VO")
@Data
public class ProjectSprintRespVO implements VO {

    @Schema(description = "迭代编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long projectId;

    @Schema(description = "迭代名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "第 1 迭代")
    private String title;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "NOT_STARTED")
    private String status;

    @Schema(description = "负责人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @Trans(type = TransType.AUTO_TRANS, key = AdminUserApi.PREFIX,
            fields = {"nickname", "avatar"}, refs = {"ownerUserName", "ownerUserAvatar"})
    private Long ownerUserId;

    @Schema(description = "负责人用户昵称", example = "芋道源码")
    private String ownerUserName;

    @Schema(description = "负责人用户头像", example = "https://www.iocoder.cn/1.png")
    private String ownerUserAvatar;

    @Schema(description = "开始日期", example = "2026-07-27")
    private LocalDate startDate;

    @Schema(description = "结束日期", example = "2026-08-09")
    private LocalDate endDate;

    @Schema(description = "工时容量（小时）", example = "7.5")
    private BigDecimal capacityHours;

    @Schema(description = "已用工时（小时，当前为 Mock 数据）", example = "0.0")
    private BigDecimal usedHours;

    @Schema(description = "工作项总数", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
    private Long totalWorkItemCount;

    @Schema(description = "已完成工作项数", requiredMode = Schema.RequiredMode.REQUIRED, example = "6")
    private Long completedWorkItemCount;

    @Schema(description = "迭代描述")
    private String description;

}
