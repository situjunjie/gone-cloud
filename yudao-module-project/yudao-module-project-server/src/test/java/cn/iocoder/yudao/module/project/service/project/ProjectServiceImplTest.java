package cn.iocoder.yudao.module.project.service.project;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectSaveReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.project.enums.ProjectRoleBuiltinTypeEnum;
import cn.iocoder.yudao.module.project.enums.ProjectVisibilityEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_ONLY_PRIVATE_SUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProjectServiceImpl service;

    @Mock private ProjectMapper projectMapper;
    @Mock private ProjectRoleService roleService;
    @Mock private ProjectMemberService memberService;
    @Mock private ProjectPermissionService permissionService;
    @Mock private AdminUserApi adminUserApi;

    @Test
    void testCreateProjectUsesCurrentUserAndRoleSnapshot() {
        ProjectSaveReqVO reqVO = buildReq(ProjectVisibilityEnum.PRIVATE.getValue());
        doAnswer(invocation -> {
            ProjectDO project = invocation.getArgument(0);
            project.setId(100L);
            return 1;
        }).when(projectMapper).insert(any(ProjectDO.class));
        Map<String, Long> builtinRoles = Map.of(
                ProjectRoleBuiltinTypeEnum.ADMIN.getValue(), 10L,
                ProjectRoleBuiltinTypeEnum.PARTICIPANT.getValue(), 11L);
        when(roleService.snapshotGlobalRoles(100L)).thenReturn(builtinRoles);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1L);
            assertEquals(100L, service.createProject(reqVO));
        }

        verify(roleService).snapshotGlobalRoles(100L);
        verify(memberService).initializeProjectMembers(100L, 1L, 2L, builtinRoles);
    }

    @Test
    void testCreatePublicProjectRejected() {
        ProjectSaveReqVO reqVO = buildReq(ProjectVisibilityEnum.PUBLIC.getValue());
        assertServiceException(() -> service.createProject(reqVO), PROJECT_ONLY_PRIVATE_SUPPORTED);
    }

    private ProjectSaveReqVO buildReq(String visibility) {
        ProjectSaveReqVO reqVO = new ProjectSaveReqVO();
        reqVO.setName("研发项目");
        reqVO.setCode("PROJECT-1");
        reqVO.setVisibility(visibility);
        reqVO.setOwnerUserId(2L);
        return reqVO;
    }

}
