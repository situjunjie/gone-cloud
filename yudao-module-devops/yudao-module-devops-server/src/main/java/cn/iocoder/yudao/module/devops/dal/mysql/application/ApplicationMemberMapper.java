package cn.iocoder.yudao.module.devops.dal.mysql.application;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationMemberDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface ApplicationMemberMapper extends BaseMapperX<ApplicationMemberDO> {

    default List<ApplicationMemberDO> selectListByAppId(Long appId) {
        return selectList(new LambdaQueryWrapperX<ApplicationMemberDO>()
                .eq(ApplicationMemberDO::getAppId, appId)
                .orderByAsc(ApplicationMemberDO::getCreateTime)
                .orderByAsc(ApplicationMemberDO::getId));
    }

    default ApplicationMemberDO selectByAppIdAndUserId(Long appId, Long userId) {
        return selectOne(ApplicationMemberDO::getAppId, appId, ApplicationMemberDO::getUserId, userId);
    }

    @Select("""
            <script>
            SELECT id, app_id, user_id,
                   creator, create_time, updater, update_time, deleted, tenant_id
            FROM dev_application_member
            WHERE tenant_id = #{tenantId}
              AND app_id = #{appId}
              AND user_id IN
              <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                  #{userId}
              </foreach>
            </script>
            """)
    List<ApplicationMemberDO> selectListByTenantIdAndAppIdAndUserIdsIncludingDeleted(
            @Param("tenantId") Long tenantId, @Param("appId") Long appId,
            @Param("userIds") Collection<Long> userIds);

    @Select("""
            SELECT id, app_id, user_id,
                   creator, create_time, updater, update_time, deleted, tenant_id
            FROM dev_application_member
            WHERE tenant_id = #{tenantId}
              AND app_id = #{appId}
              AND user_id = #{userId}
            """)
    ApplicationMemberDO selectByTenantIdAndAppIdAndUserIdIncludingDeleted(@Param("tenantId") Long tenantId,
                                                                          @Param("appId") Long appId,
                                                                          @Param("userId") Long userId);

    @Update("""
            UPDATE dev_application_member
            SET deleted = 0,
                update_time = #{updateTime}
            WHERE id = #{id}
              AND tenant_id = #{tenantId}
            """)
    int restoreById(@Param("id") Long id, @Param("tenantId") Long tenantId,
                    @Param("updateTime") LocalDateTime updateTime);

}
