package cn.iocoder.yudao.module.project.service.sprint;

import cn.iocoder.yudao.module.project.controller.admin.sprint.vo.ProjectSprintCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.sprint.vo.ProjectSprintRespVO;

import java.util.List;

public interface ProjectSprintService {

    Long createSprint(ProjectSprintCreateReqVO createReqVO);

    List<ProjectSprintRespVO> getSprintList(Long projectId);

}
