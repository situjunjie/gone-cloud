package cn.iocoder.yudao.module.project.dal.dataobject.comment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("proj_comment_mention")
@KeySequence("proj_comment_mention_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CommentMentionDO extends TenantBaseDO {

    @TableId
    private Long id;
    /** 评论编号。 */
    private Long commentId;
    /** 被 @ 的用户编号。 */
    private Long mentionedUserId;

}
