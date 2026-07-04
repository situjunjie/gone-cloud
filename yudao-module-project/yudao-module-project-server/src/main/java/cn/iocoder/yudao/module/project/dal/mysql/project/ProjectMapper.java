package cn.iocoder.yudao.module.project.dal.mysql.project;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapperX<ProjectDO> {

    default ProjectDO selectByCode(String code) {
        return selectOne(ProjectDO::getCode, code);
    }

    default PageResult<ProjectDO> selectPage(ProjectPageReqVO reqVO) {
        LambdaQueryWrapperX<ProjectDO> query = new LambdaQueryWrapperX<ProjectDO>()
                .eqIfPresent(ProjectDO::getVisibility, reqVO.getVisibility())
                .eqIfPresent(ProjectDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ProjectDO::getOwnerUserId, reqVO.getOwnerUserId())
                .betweenIfPresent(ProjectDO::getCreateTime, reqVO.getCreateTime());
        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            query.and(q -> q.like(ProjectDO::getCode, reqVO.getKeyword())
                    .or().like(ProjectDO::getName, reqVO.getKeyword()));
        }
        query.orderByDesc(ProjectDO::getId);
        return selectPage(reqVO, query);
    }

}
