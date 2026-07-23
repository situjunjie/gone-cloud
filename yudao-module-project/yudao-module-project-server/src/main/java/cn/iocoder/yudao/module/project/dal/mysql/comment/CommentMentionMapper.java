package cn.iocoder.yudao.module.project.dal.mysql.comment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.dal.dataobject.comment.CommentMentionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface CommentMentionMapper extends BaseMapperX<CommentMentionDO> {

    default List<CommentMentionDO> selectListByCommentId(Long commentId) {
        return selectList(CommentMentionDO::getCommentId, commentId);
    }

    default List<CommentMentionDO> selectListByCommentIds(Collection<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<CommentMentionDO>()
                .in(CommentMentionDO::getCommentId, commentIds));
    }

    @Select("""
            SELECT id, comment_id, mentioned_user_id,
                   creator, create_time, updater, update_time, deleted, tenant_id
            FROM proj_comment_mention
            WHERE tenant_id = #{tenantId} AND comment_id = #{commentId}
            """)
    List<CommentMentionDO> selectListIncludingDeleted(@Param("tenantId") Long tenantId,
                                                             @Param("commentId") Long commentId);

    @Update("""
            UPDATE proj_comment_mention
            SET deleted = 0, update_time = #{updateTime}
            WHERE id = #{id} AND tenant_id = #{tenantId}
            """)
    int restoreById(@Param("id") Long id, @Param("tenantId") Long tenantId,
                    @Param("updateTime") LocalDateTime updateTime);

    default int deleteListByCommentIdAndUserIds(Long commentId, Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        return delete(new LambdaQueryWrapperX<CommentMentionDO>()
                .eq(CommentMentionDO::getCommentId, commentId)
                .in(CommentMentionDO::getMentionedUserId, userIds));
    }

    default int deleteListByCommentId(Long commentId) {
        return delete(CommentMentionDO::getCommentId, commentId);
    }

}
