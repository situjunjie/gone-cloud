package cn.iocoder.yudao.module.project.controller.admin.comment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentRespVO;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentUpdateReqVO;
import cn.iocoder.yudao.module.project.service.comment.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 项目评论")
@RestController
@RequestMapping("/project/comment")
@Validated
public class CommentController {

    @Resource
    private CommentService commentService;

    @PostMapping("/create")
    @Operation(summary = "创建项目评论")
    @PreAuthorize("@projectSecurity.hasCommentTargetPermission(#reqVO.targetType, #reqVO.targetId, 'COMMENT_CREATE')")
    public CommonResult<Long> createComment(@Valid @RequestBody CommentCreateReqVO reqVO) {
        return success(commentService.createComment(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目评论")
    @PreAuthorize("@projectSecurity.hasCommentPermission(#reqVO.id, 'COMMENT_UPDATE')")
    public CommonResult<Boolean> updateComment(@Valid @RequestBody CommentUpdateReqVO reqVO) {
        commentService.updateComment(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目评论")
    @Parameter(name = "id", description = "评论编号", required = true, example = "1024")
    @PreAuthorize("@projectSecurity.hasCommentPermission(#id, 'COMMENT_DELETE')")
    public CommonResult<Boolean> deleteComment(@RequestParam("id") Long id) {
        commentService.deleteComment(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得项目评论分页")
    @PreAuthorize("@projectSecurity.hasCommentTargetPermission(#reqVO.targetType, #reqVO.targetId, 'COMMENT_QUERY')")
    public CommonResult<PageResult<CommentRespVO>> getCommentPage(
            @Valid CommentPageReqVO reqVO) {
        return success(commentService.getCommentPage(reqVO));
    }

}
