package cn.iocoder.yudao.module.project.dal.mysql.project;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectRoleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProjectRoleMapper extends BaseMapperX<ProjectRoleDO> {

    default List<ProjectRoleDO> selectListByScopeAndProjectId(String scope, Long projectId) {
        return selectList(new LambdaQueryWrapperX<ProjectRoleDO>()
                .eq(ProjectRoleDO::getScope, scope)
                .eq(ProjectRoleDO::getProjectId, projectId)
                .orderByAsc(ProjectRoleDO::getSort)
                .orderByAsc(ProjectRoleDO::getId));
    }

    default ProjectRoleDO selectByScopeAndProjectIdAndCode(String scope, Long projectId, String code) {
        return selectOne(ProjectRoleDO::getScope, scope, ProjectRoleDO::getProjectId, projectId,
                ProjectRoleDO::getCode, code);
    }

    default ProjectRoleDO selectBuiltinByProjectId(Long projectId, String builtinType) {
        return selectOne(ProjectRoleDO::getProjectId, projectId,
                ProjectRoleDO::getBuiltinType, builtinType);
    }

    @Select("""
            SELECT id, scope, project_id, code, name, builtin_type, sort, remark,
                   creator, create_time, updater, update_time, deleted, tenant_id
            FROM proj_project_role
            WHERE tenant_id = #{tenantId} AND scope = #{scope}
              AND project_id = #{projectId} AND code = #{code}
            """)
    ProjectRoleDO selectIncludingDeleted(@Param("tenantId") Long tenantId, @Param("scope") String scope,
                                         @Param("projectId") Long projectId, @Param("code") String code);

    @Update("""
            UPDATE proj_project_role
            SET name = #{name}, builtin_type = #{builtinType}, sort = #{sort}, remark = #{remark},
                deleted = 0, update_time = #{updateTime}
            WHERE id = #{id} AND tenant_id = #{tenantId}
            """)
    int restoreById(@Param("id") Long id, @Param("tenantId") Long tenantId,
                    @Param("name") String name, @Param("builtinType") String builtinType,
                    @Param("sort") Integer sort, @Param("remark") String remark,
                    @Param("updateTime") LocalDateTime updateTime);

}
