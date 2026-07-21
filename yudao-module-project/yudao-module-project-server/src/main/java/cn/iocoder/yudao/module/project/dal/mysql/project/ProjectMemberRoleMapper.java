package cn.iocoder.yudao.module.project.dal.mysql.project;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectMemberRoleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface ProjectMemberRoleMapper extends BaseMapperX<ProjectMemberRoleDO> {

    default List<ProjectMemberRoleDO> selectListByMemberId(Long memberId) {
        return selectList(ProjectMemberRoleDO::getMemberId, memberId);
    }

    default List<ProjectMemberRoleDO> selectListByMemberIds(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<ProjectMemberRoleDO>()
                .in(ProjectMemberRoleDO::getMemberId, memberIds));
    }

    default Long selectCountByRoleId(Long roleId) {
        return selectCount(ProjectMemberRoleDO::getRoleId, roleId);
    }

    @Select("""
            SELECT id, project_id, member_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id
            FROM proj_project_member_role
            WHERE tenant_id = #{tenantId} AND project_id = #{projectId}
              AND member_id = #{memberId} AND role_id = #{roleId}
            """)
    ProjectMemberRoleDO selectIncludingDeleted(@Param("tenantId") Long tenantId,
                                               @Param("projectId") Long projectId,
                                               @Param("memberId") Long memberId,
                                               @Param("roleId") Long roleId);

    @Update("""
            UPDATE proj_project_member_role
            SET deleted = 0, update_time = #{updateTime}
            WHERE id = #{id} AND tenant_id = #{tenantId}
            """)
    int restoreById(@Param("id") Long id, @Param("tenantId") Long tenantId,
                    @Param("updateTime") LocalDateTime updateTime);

    default int deleteListByMemberIdAndRoleIds(Long memberId, Collection<Long> roleIds) {
        return delete(new LambdaQueryWrapperX<ProjectMemberRoleDO>()
                .eq(ProjectMemberRoleDO::getMemberId, memberId)
                .in(ProjectMemberRoleDO::getRoleId, roleIds));
    }

    default int deleteListByMemberId(Long memberId) {
        return delete(ProjectMemberRoleDO::getMemberId, memberId);
    }

}
