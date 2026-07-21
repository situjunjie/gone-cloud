package cn.iocoder.yudao.module.project.dal.mysql.project;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectMemberDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProjectMemberMapper extends BaseMapperX<ProjectMemberDO> {

    default ProjectMemberDO selectByProjectIdAndUserId(Long projectId, Long userId) {
        return selectOne(ProjectMemberDO::getProjectId, projectId, ProjectMemberDO::getUserId, userId);
    }

    default List<ProjectMemberDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<ProjectMemberDO>()
                .eq(ProjectMemberDO::getProjectId, projectId)
                .orderByDesc(ProjectMemberDO::getId));
    }

    default Long selectCountByProjectId(Long projectId) {
        return selectCount(ProjectMemberDO::getProjectId, projectId);
    }

    @Select("""
            SELECT id, project_id, user_id,
                   creator, create_time, updater, update_time, deleted, tenant_id
            FROM proj_project_member
            WHERE tenant_id = #{tenantId}
              AND project_id = #{projectId}
              AND user_id = #{userId}
            """)
    ProjectMemberDO selectByTenantIdAndProjectIdAndUserIdIncludingDeleted(@Param("tenantId") Long tenantId,
                                                                          @Param("projectId") Long projectId,
                                                                          @Param("userId") Long userId);

    @Update("""
            UPDATE proj_project_member
            SET deleted = 0,
                update_time = #{updateTime}
            WHERE id = #{id}
              AND tenant_id = #{tenantId}
            """)
    int restoreById(@Param("id") Long id, @Param("tenantId") Long tenantId,
                    @Param("updateTime") LocalDateTime updateTime);

}
