package cn.iocoder.yudao.module.project.api.workitem;

import cn.hutool.core.convert.Convert;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.project.api.workitem.dto.ProjectWorkItemTitleRespDTO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectWorkItemApiImpl implements ProjectWorkItemApi {

    @Resource
    private ProjectWorkItemMapper projectWorkItemMapper;

    @Override
    public List<ProjectWorkItemTitleRespDTO> selectByIds(List<?> ids) {
        List<ProjectWorkItemDO> workItems = projectWorkItemMapper.selectByIds(Convert.toList(Long.class, ids));
        return BeanUtils.toBean(workItems, ProjectWorkItemTitleRespDTO.class);
    }

    @Override
    public ProjectWorkItemTitleRespDTO selectById(Object id) {
        ProjectWorkItemDO workItem = projectWorkItemMapper.selectById(Convert.toLong(id));
        return BeanUtils.toBean(workItem, ProjectWorkItemTitleRespDTO.class);
    }

}
