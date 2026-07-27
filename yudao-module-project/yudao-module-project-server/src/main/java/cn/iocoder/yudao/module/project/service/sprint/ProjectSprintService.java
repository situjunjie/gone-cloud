package cn.iocoder.yudao.module.project.service.sprint;

import cn.iocoder.yudao.module.project.controller.admin.sprint.vo.ProjectSprintCreateReqVO;

public interface ProjectSprintService {

    Long createSprint(ProjectSprintCreateReqVO createReqVO);

}
