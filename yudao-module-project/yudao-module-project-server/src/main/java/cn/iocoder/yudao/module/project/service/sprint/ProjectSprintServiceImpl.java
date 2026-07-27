package cn.iocoder.yudao.module.project.service.sprint;

import cn.iocoder.yudao.module.project.controller.admin.sprint.vo.ProjectSprintCreateReqVO;
import cn.iocoder.yudao.module.project.convert.project.ProjectSprintConvert;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectSprintDO;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectSprintMapper;
import cn.iocoder.yudao.module.project.enums.ProjectSprintStatusEnum;
import cn.iocoder.yudao.module.project.service.project.ProjectMemberService;
import cn.iocoder.yudao.module.project.service.project.ProjectService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_SPRINT_OWNER_NOT_MEMBER;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_SPRINT_PLANNED_DATE_INVALID;

@Service
@Validated
public class ProjectSprintServiceImpl implements ProjectSprintService {

    @Resource
    private ProjectSprintMapper projectSprintMapper;
    @Resource
    private ProjectService projectService;
    @Resource
    private ProjectMemberService projectMemberService;

    @Override
    public Long createSprint(ProjectSprintCreateReqVO createReqVO) {
        validatePlannedDate(createReqVO.getStartDate(), createReqVO.getEndDate());
        projectService.validateProjectUsable(createReqVO.getProjectId());
        if (!projectMemberService.isProjectMember(createReqVO.getProjectId(), createReqVO.getOwnerUserId())) {
            throw exception(PROJECT_SPRINT_OWNER_NOT_MEMBER);
        }

        ProjectSprintDO sprint = ProjectSprintConvert.INSTANCE.convert(createReqVO);
        sprint.setStatus(ProjectSprintStatusEnum.NOT_STARTED.getValue());
        projectSprintMapper.insert(sprint);
        return sprint.getId();
    }

    private void validatePlannedDate(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw exception(PROJECT_SPRINT_PLANNED_DATE_INVALID);
        }
    }

}
