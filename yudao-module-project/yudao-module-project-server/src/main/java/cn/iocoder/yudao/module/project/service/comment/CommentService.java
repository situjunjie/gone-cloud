package cn.iocoder.yudao.module.project.service.comment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentRespVO;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentUpdateReqVO;
import jakarta.validation.Valid;

public interface CommentService {

    Long createComment(@Valid CommentCreateReqVO reqVO);

    void updateComment(@Valid CommentUpdateReqVO reqVO);

    void deleteComment(Long id);

    PageResult<CommentRespVO> getCommentPage(@Valid CommentPageReqVO reqVO);

}
