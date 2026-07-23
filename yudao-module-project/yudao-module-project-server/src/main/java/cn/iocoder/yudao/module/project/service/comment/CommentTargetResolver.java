package cn.iocoder.yudao.module.project.service.comment;

import cn.iocoder.yudao.module.project.enums.CommentTargetTypeEnum;

public interface CommentTargetResolver {

    CommentTargetTypeEnum getType();

    CommentTarget resolve(Long targetId);

}
