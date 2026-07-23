package cn.iocoder.yudao.module.project.dal.mysql.comment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentPageReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.comment.CommentDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface CommentMapper extends BaseMapperX<CommentDO> {

    default PageResult<CommentDO> selectPage(CommentPageReqVO reqVO, Long projectId) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CommentDO>()
                .eq(CommentDO::getProjectId, projectId)
                .eq(CommentDO::getTargetType, reqVO.getTargetType())
                .eq(CommentDO::getTargetId, reqVO.getTargetId())
                .orderByDesc(CommentDO::getCreateTime)
                .orderByDesc(CommentDO::getId));
    }

    default Long selectCountByTarget(String targetType, Long targetId) {
        return selectCount(new LambdaQueryWrapperX<CommentDO>()
                .eq(CommentDO::getTargetType, targetType)
                .eq(CommentDO::getTargetId, targetId));
    }

    default int updateContentByIdAndStatus(Long id, String oldStatus, String content, LocalDateTime updateTime) {
        return update(null, new LambdaUpdateWrapper<CommentDO>()
                .eq(CommentDO::getId, id)
                .eq(CommentDO::getStatus, oldStatus)
                .set(CommentDO::getContent, content)
                .set(CommentDO::getUpdateTime, updateTime));
    }

    default int markDeletedByIdAndStatus(Long id, String oldStatus, String deletedStatus, LocalDateTime updateTime) {
        return update(null, new LambdaUpdateWrapper<CommentDO>()
                .eq(CommentDO::getId, id)
                .eq(CommentDO::getStatus, oldStatus)
                .set(CommentDO::getStatus, deletedStatus)
                .set(CommentDO::getContent, null)
                .set(CommentDO::getUpdateTime, updateTime));
    }

}
