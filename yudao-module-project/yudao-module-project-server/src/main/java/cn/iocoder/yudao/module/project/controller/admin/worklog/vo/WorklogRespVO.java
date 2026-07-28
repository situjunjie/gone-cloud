package cn.iocoder.yudao.module.project.controller.admin.worklog.vo;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.core.trans.anno.Trans;
import org.dromara.core.trans.constant.TransType;
import org.dromara.core.trans.vo.VO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 项目工时 Response VO")
@Data
public class WorklogRespVO implements VO {

    private Long id;
    private Long projectId;
    private Long workItemId;
    private String workItemNo;
    private String workItemTitle;
    private String type;
    private BigDecimal hours;
    private String category;
    private String description;
    @Trans(type = TransType.AUTO_TRANS, key = AdminUserApi.PREFIX, fields = "nickname", ref = "ownerUserName")
    private Long ownerUserId;
    private String ownerUserName;
    private LocalDate workDate;
    private String creator;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
