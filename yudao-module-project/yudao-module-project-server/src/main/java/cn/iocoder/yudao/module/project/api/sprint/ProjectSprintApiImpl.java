package cn.iocoder.yudao.module.project.api.sprint;

import cn.hutool.core.convert.Convert;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.project.api.sprint.dto.ProjectSprintTitleRespDTO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectSprintDO;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectSprintMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectSprintApiImpl implements ProjectSprintApi {

    @Resource
    private ProjectSprintMapper projectSprintMapper;

    @Override
    public List<ProjectSprintTitleRespDTO> selectByIds(List<?> ids) {
        List<ProjectSprintDO> sprints = projectSprintMapper.selectByIds(Convert.toList(Long.class, ids));
        return BeanUtils.toBean(sprints, ProjectSprintTitleRespDTO.class);
    }

    @Override
    public ProjectSprintTitleRespDTO selectById(Object id) {
        ProjectSprintDO sprint = projectSprintMapper.selectById(Convert.toLong(id));
        return BeanUtils.toBean(sprint, ProjectSprintTitleRespDTO.class);
    }

}
