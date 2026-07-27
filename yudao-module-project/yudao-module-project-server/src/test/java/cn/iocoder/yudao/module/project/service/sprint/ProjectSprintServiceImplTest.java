package cn.iocoder.yudao.module.project.service.sprint;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.controller.admin.sprint.vo.ProjectSprintCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.sprint.vo.ProjectSprintRespVO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectSprintDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectSprintWorkItemCountResult;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectSprintMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import cn.iocoder.yudao.module.project.enums.ProjectSprintStatusEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemStatusEnum;
import cn.iocoder.yudao.module.project.service.project.ProjectMemberService;
import cn.iocoder.yudao.module.project.service.project.ProjectService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_ARCHIVED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_SPRINT_OWNER_NOT_MEMBER;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_SPRINT_PLANNED_DATE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectSprintServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProjectSprintServiceImpl service;

    @Mock
    private ProjectSprintMapper projectSprintMapper;
    @Mock
    private ProjectWorkItemMapper projectWorkItemMapper;
    @Mock
    private ProjectService projectService;
    @Mock
    private ProjectMemberService projectMemberService;

    @Test
    void testCreateSprint() {
        ProjectSprintCreateReqVO reqVO = buildReqVO();
        when(projectMemberService.isProjectMember(1L, 2L)).thenReturn(true);
        doAnswer(invocation -> {
            ProjectSprintDO sprint = invocation.getArgument(0);
            sprint.setId(10L);
            return 1;
        }).when(projectSprintMapper).insert(any(ProjectSprintDO.class));

        assertEquals(10L, service.createSprint(reqVO));

        verify(projectService).validateProjectUsable(1L);
        ArgumentCaptor<ProjectSprintDO> captor = ArgumentCaptor.forClass(ProjectSprintDO.class);
        verify(projectSprintMapper).insert(captor.capture());
        ProjectSprintDO sprint = captor.getValue();
        assertEquals(1L, sprint.getProjectId());
        assertEquals("第 1 迭代", sprint.getTitle());
        assertEquals(2L, sprint.getOwnerUserId());
        assertEquals(LocalDate.of(2026, 7, 27).atStartOfDay(), sprint.getPlannedStartTime());
        assertEquals(LocalDate.of(2026, 8, 9).atStartOfDay(), sprint.getPlannedEndTime());
        assertEquals(new BigDecimal("7.5"), sprint.getCapacityHours());
        assertEquals("完成核心流程联调", sprint.getDescription());
        assertEquals(ProjectSprintStatusEnum.NOT_STARTED.getValue(), sprint.getStatus());
        assertNull(sprint.getActualStartTime());
        assertNull(sprint.getActualEndTime());
    }

    @Test
    void testCreateSprintRejectsInvalidDateRangeBeforeQueries() {
        ProjectSprintCreateReqVO reqVO = buildReqVO();
        reqVO.setEndDate(reqVO.getStartDate().minusDays(1));

        assertServiceException(() -> service.createSprint(reqVO), PROJECT_SPRINT_PLANNED_DATE_INVALID);

        verifyNoInteractions(projectService, projectMemberService, projectSprintMapper);
    }

    @Test
    void testCreateSprintRejectsOwnerOutsideProject() {
        ProjectSprintCreateReqVO reqVO = buildReqVO();
        when(projectMemberService.isProjectMember(1L, 2L)).thenReturn(false);

        assertServiceException(() -> service.createSprint(reqVO), PROJECT_SPRINT_OWNER_NOT_MEMBER);

        verify(projectService).validateProjectUsable(1L);
        verify(projectSprintMapper, never()).insert(any(ProjectSprintDO.class));
    }

    @Test
    void testCreateSprintPropagatesArchivedProject() {
        ProjectSprintCreateReqVO reqVO = buildReqVO();
        when(projectService.validateProjectUsable(1L)).thenThrow(exception(PROJECT_ARCHIVED));

        assertServiceException(() -> service.createSprint(reqVO), PROJECT_ARCHIVED);

        verifyNoInteractions(projectMemberService, projectSprintMapper);
    }

    @Test
    void testGetSprintList() {
        ProjectSprintDO first = new ProjectSprintDO();
        first.setId(10L);
        first.setCapacityHours(new BigDecimal("7.5"));
        ProjectSprintDO second = new ProjectSprintDO();
        second.setId(11L);
        when(projectSprintMapper.selectListByProjectId(1L)).thenReturn(List.of(first, second));
        ProjectSprintWorkItemCountResult count = new ProjectSprintWorkItemCountResult();
        count.setSprintId(10L);
        count.setTotalCount(3L);
        count.setCompletedCount(2L);
        when(projectWorkItemMapper.selectSprintWorkItemCounts(
                1L, ProjectWorkItemStatusEnum.COMPLETED.getValue())).thenReturn(List.of(count));

        List<ProjectSprintRespVO> result = service.getSprintList(1L);

        assertEquals(2, result.size());
        assertEquals(new BigDecimal("7.5"), result.get(0).getCapacityHours());
        assertEquals(new BigDecimal("0.0"), result.get(0).getUsedHours());
        assertEquals(3L, result.get(0).getTotalWorkItemCount());
        assertEquals(2L, result.get(0).getCompletedWorkItemCount());
        assertEquals(new BigDecimal("0.0"), result.get(1).getUsedHours());
        assertEquals(0L, result.get(1).getTotalWorkItemCount());
        assertEquals(0L, result.get(1).getCompletedWorkItemCount());
        verify(projectService).validateProjectExists(1L);
    }

    @Test
    void testGetSprintListReturnsEmptyList() {
        when(projectSprintMapper.selectListByProjectId(1L)).thenReturn(List.of());

        assertEquals(List.of(), service.getSprintList(1L));

        verify(projectService).validateProjectExists(1L);
        verifyNoInteractions(projectWorkItemMapper);
    }

    @Test
    void testGetSprintListRejectsMissingProject() {
        when(projectService.validateProjectExists(1L)).thenThrow(exception(PROJECT_NOT_EXISTS));

        assertServiceException(() -> service.getSprintList(1L), PROJECT_NOT_EXISTS);

        verifyNoInteractions(projectSprintMapper, projectWorkItemMapper);
    }

    private ProjectSprintCreateReqVO buildReqVO() {
        ProjectSprintCreateReqVO reqVO = new ProjectSprintCreateReqVO();
        reqVO.setProjectId(1L);
        reqVO.setTitle("第 1 迭代");
        reqVO.setOwnerUserId(2L);
        reqVO.setStartDate(LocalDate.of(2026, 7, 27));
        reqVO.setEndDate(LocalDate.of(2026, 8, 9));
        reqVO.setCapacityHours(new BigDecimal("7.5"));
        reqVO.setDescription("完成核心流程联调");
        return reqVO;
    }

}
