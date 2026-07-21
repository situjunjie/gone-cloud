package cn.iocoder.yudao.module.project.service.project;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMemberAddReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMemberUpdateRoleReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectMemberDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectMemberRoleDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectRoleDO;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMemberMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMemberRoleMapper;
import cn.iocoder.yudao.module.project.enums.ProjectRoleBuiltinTypeEnum;
import cn.iocoder.yudao.module.project.enums.ProjectRoleScopeEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_OWNER_ADMIN_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class ProjectMemberServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProjectMemberServiceImpl service;

    @Mock private ProjectMapper projectMapper;
    @Mock private ProjectMemberMapper memberMapper;
    @Mock private ProjectMemberRoleMapper memberRoleMapper;
    @Mock private ProjectRoleService roleService;
    @Mock private ProjectPermissionCacheService cacheService;
    @Mock private AdminUserApi adminUserApi;

    @BeforeEach
    void setUpTenant() {
        TenantContextHolder.setTenantId(10L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void testNewMemberDefaultsToParticipant() {
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        when(projectMapper.selectById(1L)).thenReturn(project);
        ProjectRoleDO participant = buildRole(20L, ProjectRoleBuiltinTypeEnum.PARTICIPANT.getValue());
        when(roleService.getProjectBuiltinRole(1L, ProjectRoleBuiltinTypeEnum.PARTICIPANT.getValue()))
                .thenReturn(participant);
        when(roleService.validateProjectRole(20L, 1L)).thenReturn(participant);
        when(memberMapper.selectByTenantIdAndProjectIdAndUserIdIncludingDeleted(10L, 1L, 2L)).thenReturn(null);
        when(memberRoleMapper.selectListByMemberId(100L)).thenReturn(List.of());
        when(memberRoleMapper.selectIncludingDeleted(10L, 1L, 100L, 20L)).thenReturn(null);
        doAnswer(invocation -> {
            ProjectMemberDO member = invocation.getArgument(0);
            member.setId(100L);
            return 1;
        }).when(memberMapper).insert(any(ProjectMemberDO.class));
        ProjectMemberAddReqVO reqVO = new ProjectMemberAddReqVO();
        reqVO.setProjectId(1L);
        reqVO.setUserId(2L);

        assertEquals(100L, service.addProjectMember(reqVO));
        ArgumentCaptor<ProjectMemberRoleDO> captor = ArgumentCaptor.forClass(ProjectMemberRoleDO.class);
        verify(memberRoleMapper).insert(captor.capture());
        assertEquals(20L, captor.getValue().getRoleId());
    }

    @Test
    void testInitializeCreatorAndDifferentOwnerRoles() {
        ProjectRoleDO admin = buildRole(10L, ProjectRoleBuiltinTypeEnum.ADMIN.getValue());
        ProjectRoleDO participant = buildRole(20L, ProjectRoleBuiltinTypeEnum.PARTICIPANT.getValue());
        when(roleService.validateProjectRole(10L, 1L)).thenReturn(admin);
        when(roleService.validateProjectRole(20L, 1L)).thenReturn(participant);
        AtomicLong memberId = new AtomicLong(99L);
        doAnswer(invocation -> {
            ProjectMemberDO member = invocation.getArgument(0);
            member.setId(memberId.incrementAndGet());
            return 1;
        }).when(memberMapper).insert(any(ProjectMemberDO.class));

        service.initializeProjectMembers(1L, 1L, 2L, Map.of(
                ProjectRoleBuiltinTypeEnum.ADMIN.getValue(), 10L,
                ProjectRoleBuiltinTypeEnum.PARTICIPANT.getValue(), 20L));

        ArgumentCaptor<ProjectMemberRoleDO> captor = ArgumentCaptor.forClass(ProjectMemberRoleDO.class);
        verify(memberRoleMapper, times(3)).insert(captor.capture());
        Set<String> assignments = captor.getAllValues().stream()
                .map(item -> item.getMemberId() + ":" + item.getRoleId())
                .collect(Collectors.toSet());
        assertEquals(Set.of("100:10", "100:20", "101:10"), assignments);
    }

    @Test
    void testOwnerMustKeepAdminRole() {
        ProjectMemberDO owner = new ProjectMemberDO();
        owner.setId(100L);
        owner.setProjectId(1L);
        owner.setUserId(2L);
        ProjectDO project = new ProjectDO();
        project.setId(1L);
        project.setOwnerUserId(2L);
        when(memberMapper.selectById(100L)).thenReturn(owner);
        when(projectMapper.selectById(1L)).thenReturn(project);
        when(roleService.validateProjectRole(30L, 1L))
                .thenReturn(buildRole(30L, ProjectRoleBuiltinTypeEnum.NONE.getValue()));
        ProjectMemberUpdateRoleReqVO reqVO = new ProjectMemberUpdateRoleReqVO();
        reqVO.setId(100L);
        reqVO.setRoleIds(Set.of(30L));

        assertServiceException(() -> service.updateProjectMemberRoles(reqVO), PROJECT_OWNER_ADMIN_REQUIRED);
    }

    private ProjectRoleDO buildRole(Long id, String builtinType) {
        ProjectRoleDO role = new ProjectRoleDO();
        role.setId(id);
        role.setProjectId(1L);
        role.setScope(ProjectRoleScopeEnum.PROJECT.getValue());
        role.setBuiltinType(builtinType);
        return role;
    }

}
