package cn.iocoder.yudao.module.project.service.sprint;

import cn.iocoder.yudao.module.project.controller.admin.sprint.vo.ProjectSprintCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.sprint.vo.ProjectSprintRespVO;
import cn.iocoder.yudao.module.project.controller.admin.sprint.vo.ProjectSprintSimpleRespVO;
import cn.iocoder.yudao.module.project.convert.project.ProjectSprintConvert;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectSprintDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectSprintWorkItemCountResult;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectSprintMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import cn.iocoder.yudao.module.project.enums.ProjectSprintStatusEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemStatusEnum;
import cn.iocoder.yudao.module.project.service.project.ProjectMemberService;
import cn.iocoder.yudao.module.project.service.project.ProjectService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_SPRINT_OWNER_NOT_MEMBER;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_SPRINT_PLANNED_DATE_INVALID;

@Service
@Validated
public class ProjectSprintServiceImpl implements ProjectSprintService {

    private static final BigDecimal MOCK_USED_HOURS = new BigDecimal("0.0");

    @Resource
    private ProjectSprintMapper projectSprintMapper;
    @Resource
    private ProjectWorkItemMapper projectWorkItemMapper;
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

    @Override
    public List<ProjectSprintRespVO> getSprintList(Long projectId) {
        projectService.validateProjectExists(projectId);
        List<ProjectSprintRespVO> sprints = ProjectSprintConvert.INSTANCE.convertList(
                projectSprintMapper.selectListByProjectId(projectId));
        if (sprints.isEmpty()) {
            return sprints;
        }
        Map<Long, ProjectSprintWorkItemCountResult> countMap = projectWorkItemMapper
                .selectSprintWorkItemCounts(projectId, ProjectWorkItemStatusEnum.COMPLETED.getValue())
                .stream().collect(Collectors.toMap(ProjectSprintWorkItemCountResult::getSprintId,
                        Function.identity()));
        sprints.forEach(sprint -> {
            ProjectSprintWorkItemCountResult count = countMap.get(sprint.getId());
            sprint.setTotalWorkItemCount(count != null ? count.getTotalCount() : 0L);
            sprint.setCompletedWorkItemCount(count != null ? count.getCompletedCount() : 0L);
            sprint.setUsedHours(MOCK_USED_HOURS);
        });
        return sprints;
    }

    @Override
    public List<ProjectSprintSimpleRespVO> getSprintSimpleList(Long projectId) {
        projectService.validateProjectExists(projectId);
        return ProjectSprintConvert.INSTANCE.convertSimpleList(
                projectSprintMapper.selectListByProjectId(projectId));
    }

    private void validatePlannedDate(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw exception(PROJECT_SPRINT_PLANNED_DATE_INVALID);
        }
    }

}
