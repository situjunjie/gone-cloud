package cn.iocoder.yudao.module.project.dal.mysql.project;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectRolePermissionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface ProjectRolePermissionMapper extends BaseMapperX<ProjectRolePermissionDO> {

    default List<ProjectRolePermissionDO> selectListByRoleId(Long roleId) {
        return selectList(ProjectRolePermissionDO::getRoleId, roleId);
    }

    default List<ProjectRolePermissionDO> selectListByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<ProjectRolePermissionDO>()
                .in(ProjectRolePermissionDO::getRoleId, roleIds));
    }

    @Select("""
            SELECT id, role_id, permission_code, creator, create_time, updater, update_time, deleted, tenant_id
            FROM proj_project_role_permission
            WHERE tenant_id = #{tenantId} AND role_id = #{roleId} AND permission_code = #{permissionCode}
            """)
    ProjectRolePermissionDO selectIncludingDeleted(@Param("tenantId") Long tenantId,
                                                   @Param("roleId") Long roleId,
                                                   @Param("permissionCode") String permissionCode);

    @Update("""
            UPDATE proj_project_role_permission
            SET deleted = 0, update_time = #{updateTime}
            WHERE id = #{id} AND tenant_id = #{tenantId}
            """)
    int restoreById(@Param("id") Long id, @Param("tenantId") Long tenantId,
                    @Param("updateTime") LocalDateTime updateTime);

    default int deleteListByRoleIdAndPermissionCodes(Long roleId, Collection<String> permissionCodes) {
        return delete(new LambdaQueryWrapperX<ProjectRolePermissionDO>()
                .eq(ProjectRolePermissionDO::getRoleId, roleId)
                .in(ProjectRolePermissionDO::getPermissionCode, permissionCodes));
    }

    default int deleteListByRoleId(Long roleId) {
        return delete(ProjectRolePermissionDO::getRoleId, roleId);
    }

}
