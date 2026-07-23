package cn.iocoder.yudao.module.project.dal.dataobject.comment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.project.enums.CommentStatusEnum;
import cn.iocoder.yudao.module.project.enums.CommentTargetTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("proj_comment")
@KeySequence("proj_comment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CommentDO extends TenantBaseDO {

    @TableId
    private Long id;
    /** 归属项目编号，由目标解析得到。 */
    private Long projectId;
    /** 目标类型，枚举 {@link CommentTargetTypeEnum}。 */
    private String targetType;
    /** 目标编号。 */
    private Long targetId;
    /** 回复引用的评论编号。 */
    private Long replyToCommentId;
    /** 作者用户编号。 */
    private Long authorUserId;
    /** Markdown 正文，删除后清空。 */
    private String content;
    /** 状态，枚举 {@link CommentStatusEnum}。 */
    private String status;

}
