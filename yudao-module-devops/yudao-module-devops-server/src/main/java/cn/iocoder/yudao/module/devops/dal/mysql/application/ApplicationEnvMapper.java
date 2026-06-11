package cn.iocoder.yudao.module.devops.dal.mysql.application;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface ApplicationEnvMapper extends BaseMapperX<ApplicationEnvDO> {

    default List<ApplicationEnvDO> selectListByAppId(Long appId) {
        return selectList(ApplicationEnvDO::getAppId, appId);
    }

    default List<ApplicationEnvDO> selectListByAppIdOrderByDisplayOrder(Long appId) {
        return selectList(new LambdaQueryWrapperX<ApplicationEnvDO>()
                .eq(ApplicationEnvDO::getAppId, appId)
                .orderByAsc(ApplicationEnvDO::getDisplayOrder)
                .orderByAsc(ApplicationEnvDO::getId));
    }

    default List<ApplicationEnvDO> selectListByEnvId(Long envId) {
        return selectList(ApplicationEnvDO::getEnvId, envId);
    }

    default ApplicationEnvDO selectByAppIdAndEnvId(Long appId, Long envId) {
        return selectOne(ApplicationEnvDO::getAppId, appId, ApplicationEnvDO::getEnvId, envId);
    }

    @Select("""
            <script>
            SELECT id, app_id, env_id, display_order, pipeline_definition_id,
                   current_snapshot_id, status, remark,
                   creator, create_time, updater, update_time, deleted, tenant_id
            FROM dev_application_env
            WHERE tenant_id = #{tenantId}
              AND app_id = #{appId}
              AND env_id IN
              <foreach collection="envIds" item="envId" open="(" separator="," close=")">
                  #{envId}
              </foreach>
            </script>
            """)
    List<ApplicationEnvDO> selectListByTenantIdAndAppIdAndEnvIdsIncludingDeleted(@Param("tenantId") Long tenantId,
                                                                                 @Param("appId") Long appId,
                                                                                 @Param("envIds") Collection<Long> envIds);

    default List<ApplicationEnvDO> selectListByIds(Collection<Long> ids) {
        return selectList(ApplicationEnvDO::getId, ids);
    }

    default int deleteByAppId(Long appId) {
        return delete(ApplicationEnvDO::getAppId, appId);
    }

    default int deleteByAppIdAndEnvIds(Long appId, Collection<Long> envIds) {
        if (envIds == null || envIds.isEmpty()) {
            return 0;
        }
        return delete(new LambdaQueryWrapperX<ApplicationEnvDO>()
                .eq(ApplicationEnvDO::getAppId, appId)
                .in(ApplicationEnvDO::getEnvId, envIds));
    }

    default int updateConfigById(ApplicationEnvDO env, LocalDateTime updateTime) {
        return update(null, new LambdaUpdateWrapper<ApplicationEnvDO>()
                .eq(ApplicationEnvDO::getId, env.getId())
                .set(ApplicationEnvDO::getDisplayOrder, env.getDisplayOrder())
                .set(ApplicationEnvDO::getPipelineDefinitionId, env.getPipelineDefinitionId())
                .set(ApplicationEnvDO::getStatus, env.getStatus())
                .set(ApplicationEnvDO::getRemark, env.getRemark())
                .set(ApplicationEnvDO::getUpdateTime, updateTime));
    }

    @Update("""
            UPDATE dev_application_env
            SET display_order = #{env.displayOrder},
                pipeline_definition_id = #{env.pipelineDefinitionId},
                status = #{env.status},
                remark = #{env.remark},
                deleted = 0,
                update_time = #{updateTime}
            WHERE id = #{env.id}
              AND tenant_id = #{tenantId}
            """)
    int restoreConfigById(@Param("env") ApplicationEnvDO env,
                          @Param("tenantId") Long tenantId,
                          @Param("updateTime") LocalDateTime updateTime);

}
