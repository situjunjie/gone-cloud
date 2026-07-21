package cn.iocoder.yudao.module.project.dal.mysql.project;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface ProjectMapper extends BaseMapperX<ProjectDO> {

    default ProjectDO selectByCode(String code) {
        return selectOne(ProjectDO::getCode, code);
    }

    default int updateAnnouncementById(Long id, String announcement, LocalDateTime updateTime) {
        return update(null, new LambdaUpdateWrapper<ProjectDO>()
                .eq(ProjectDO::getId, id)
                .set(ProjectDO::getAnnouncement, announcement)
                .set(ProjectDO::getUpdateTime, updateTime));
    }

    default PageResult<ProjectDO> selectPage(ProjectPageReqVO reqVO, Long userId) {
        LambdaQueryWrapperX<ProjectDO> query = new LambdaQueryWrapperX<ProjectDO>()
                .eqIfPresent(ProjectDO::getVisibility, reqVO.getVisibility())
                .eqIfPresent(ProjectDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ProjectDO::getOwnerUserId, reqVO.getOwnerUserId())
                .betweenIfPresent(ProjectDO::getCreateTime, reqVO.getCreateTime());
        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            query.and(q -> q.like(ProjectDO::getCode, reqVO.getKeyword())
                    .or().like(ProjectDO::getName, reqVO.getKeyword()));
        }
        query.apply("EXISTS (SELECT 1 FROM proj_project_member pm "
                + "WHERE pm.project_id = proj_project.id AND pm.tenant_id = proj_project.tenant_id "
                + "AND pm.user_id = {0} AND pm.deleted = 0)", userId);
        query.orderByDesc(ProjectDO::getId);
        return selectPage(reqVO, query);
    }

}
