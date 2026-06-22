package cn.iocoder.yudao.module.system.dal.mysql.user;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.user.UserFavoriteDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserFavoriteMapper extends BaseMapperX<UserFavoriteDO> {

    default List<UserFavoriteDO> selectListByTenantIdAndUserIdAndBizType(Long tenantId, Long userId, String bizType) {
        return selectList(new LambdaQueryWrapperX<UserFavoriteDO>()
                .eq(UserFavoriteDO::getTenantId, tenantId)
                .eq(UserFavoriteDO::getUserId, userId)
                .eq(UserFavoriteDO::getBizType, bizType)
                .orderByDesc(UserFavoriteDO::getCreateTime)
                .orderByDesc(UserFavoriteDO::getId));
    }

    default UserFavoriteDO selectByTenantIdAndUserIdAndBizTypeAndBizId(Long tenantId, Long userId,
                                                                        String bizType, Long bizId) {
        return selectOne(new LambdaQueryWrapperX<UserFavoriteDO>()
                .eq(UserFavoriteDO::getTenantId, tenantId)
                .eq(UserFavoriteDO::getUserId, userId)
                .eq(UserFavoriteDO::getBizType, bizType)
                .eq(UserFavoriteDO::getBizId, bizId));
    }

    @Select("""
            SELECT id, user_id, biz_type, biz_id,
                   creator, create_time, updater, update_time, deleted, tenant_id
            FROM system_user_favorite
            WHERE tenant_id = #{tenantId}
              AND user_id = #{userId}
              AND biz_type = #{bizType}
              AND biz_id = #{bizId}
            """)
    UserFavoriteDO selectByTenantIdAndUserIdAndBizTypeAndBizIdIncludingDeleted(@Param("tenantId") Long tenantId,
                                                                                @Param("userId") Long userId,
                                                                                @Param("bizType") String bizType,
                                                                                @Param("bizId") Long bizId);

    @Update("""
            UPDATE system_user_favorite
            SET deleted = 0,
                update_time = #{updateTime}
            WHERE id = #{id}
            """)
    int restoreById(@Param("id") Long id, @Param("updateTime") LocalDateTime updateTime);

    default int deleteByTenantIdAndUserIdAndBizTypeAndBizId(Long tenantId, Long userId, String bizType, Long bizId) {
        return delete(new LambdaQueryWrapperX<UserFavoriteDO>()
                .eq(UserFavoriteDO::getTenantId, tenantId)
                .eq(UserFavoriteDO::getUserId, userId)
                .eq(UserFavoriteDO::getBizType, bizType)
                .eq(UserFavoriteDO::getBizId, bizId));
    }

}
