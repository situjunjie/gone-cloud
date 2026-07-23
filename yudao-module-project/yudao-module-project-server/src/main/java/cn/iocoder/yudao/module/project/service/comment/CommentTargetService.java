package cn.iocoder.yudao.module.project.service.comment;

import cn.iocoder.yudao.module.project.enums.CommentTargetTypeEnum;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_COMMENT_TARGET_TYPE_INVALID;

@Service
public class CommentTargetService {

    private final Map<CommentTargetTypeEnum, CommentTargetResolver> resolvers;

    public CommentTargetService(List<CommentTargetResolver> resolverList) {
        Map<CommentTargetTypeEnum, CommentTargetResolver> resolverMap =
                new EnumMap<>(CommentTargetTypeEnum.class);
        resolverList.forEach(resolver -> resolverMap.put(resolver.getType(), resolver));
        this.resolvers = Map.copyOf(resolverMap);
    }

    public CommentTarget resolve(String targetType, Long targetId) {
        CommentTargetTypeEnum type = CommentTargetTypeEnum.of(targetType);
        CommentTargetResolver resolver = type == null ? null : resolvers.get(type);
        if (resolver == null) {
            throw exception(PROJECT_COMMENT_TARGET_TYPE_INVALID, targetType);
        }
        return resolver.resolve(targetId);
    }

}
