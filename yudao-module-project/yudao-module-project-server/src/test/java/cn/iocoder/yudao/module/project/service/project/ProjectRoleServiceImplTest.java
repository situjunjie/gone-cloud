package cn.iocoder.yudao.module.project.service.project;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectRoleAssignPermissionsReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectRoleUpdateReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectRoleDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectRolePermissionDO;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMemberRoleMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectRoleMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectRolePermissionMapper;
import cn.iocoder.yudao.module.project.enums.ProjectPermissionEnum;
import cn.iocoder.yudao.module.project.enums.ProjectRoleBuiltinTypeEnum;
import cn.iocoder.yudao.module.project.enums.ProjectRoleScopeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_ADMIN_PERMISSION_IMMUTABLE;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_BUILTIN_ROLE_IMMUTABLE;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_PARTICIPANT_PERMISSION_REQUIRED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_ROLE_IN_USE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;

class ProjectRoleServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProjectRoleServiceImpl service;

    @Mock
    private ProjectRoleMapper roleMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private ProjectRolePermissionMapper rolePermissionMapper;
    @Mock
    private ProjectMemberRoleMapper memberRoleMapper;
    @Mock
    private ProjectPermissionCacheService cacheService;

    @BeforeEach
    void setUpTenant() {
        TenantContextHolder.setTenantId(10L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void testPermissionCatalogIsFixed() {
        assertEquals(ProjectPermissionEnum.ALL_CODES,
                service.getPermissionList().stream().map(item -> item.getCode()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(ProjectPermissionEnum.PARTICIPANT_DEFAULT_CODES.containsAll(Set.of(
                "COMMENT_QUERY", "COMMENT_CREATE", "COMMENT_UPDATE", "COMMENT_DELETE")));
        assertFalse(ProjectPermissionEnum.PARTICIPANT_REQUIRED_CODES.contains("COMMENT_QUERY"));
    }

    @Test
    void testAdminPermissionsCannotBeAssigned() {
        ProjectRoleDO role = buildRole(ProjectRoleBuiltinTypeEnum.ADMIN.getValue());
        when(roleMapper.selectById(1L)).thenReturn(role);
        ProjectRoleAssignPermissionsReqVO reqVO = assignReq(Set.of("PROJECT_QUERY"));

        assertServiceException(() -> service.assignProjectRolePermissions(reqVO),
                PROJECT_ADMIN_PERMISSION_IMMUTABLE);
    }

    @Test
    void testParticipantRequiredPermissionCannotBeRemoved() {
        ProjectRoleDO role = buildRole(ProjectRoleBuiltinTypeEnum.PARTICIPANT.getValue());
        when(roleMapper.selectById(1L)).thenReturn(role);
        ProjectRoleAssignPermissionsReqVO reqVO = assignReq(Set.of("PROJECT_QUERY"));

        assertServiceException(() -> service.assignProjectRolePermissions(reqVO),
                PROJECT_PARTICIPANT_PERMISSION_REQUIRED, "WORK_ITEM_QUERY");
    }

    @Test
    void testBuiltinRoleCannotBeRenamed() {
        when(roleMapper.selectById(1L)).thenReturn(buildRole(ProjectRoleBuiltinTypeEnum.PARTICIPANT.getValue()));
        ProjectRoleUpdateReqVO reqVO = new ProjectRoleUpdateReqVO();
        reqVO.setId(1L);
        reqVO.setName("新名称");

        assertServiceException(() -> service.updateProjectRole(reqVO), PROJECT_BUILTIN_ROLE_IMMUTABLE);
    }

    @Test
    void testRoleInUseCannotBeDeleted() {
        when(roleMapper.selectById(1L)).thenReturn(buildRole(ProjectRoleBuiltinTypeEnum.NONE.getValue()));
        when(memberRoleMapper.selectCountByRoleId(1L)).thenReturn(1L);

        assertServiceException(() -> service.deleteProjectRole(1L), PROJECT_ROLE_IN_USE);
    }

    @Test
    void testSnapshotCopiesRolesAndPermissionsIndependently() {
        ProjectRoleDO admin = buildGlobalRole(1L, ProjectRoleBuiltinTypeEnum.ADMIN.getValue());
        ProjectRoleDO participant = buildGlobalRole(2L, ProjectRoleBuiltinTypeEnum.PARTICIPANT.getValue());
        ProjectRoleDO developer = buildGlobalRole(3L, ProjectRoleBuiltinTypeEnum.NONE.getValue());
        developer.setCode("DEVELOPER");
        developer.setName("开发人员");
        ProjectRolePermissionDO projectQuery = permission(2L, "PROJECT_QUERY");
        ProjectRolePermissionDO workItemQuery = permission(2L, "WORK_ITEM_QUERY");
        ProjectRolePermissionDO workItemUpdate = permission(3L, "WORK_ITEM_UPDATE");
        when(roleMapper.selectIncludingDeleted(10L, ProjectRoleScopeEnum.GLOBAL.getValue(), 0L, "ADMIN"))
                .thenReturn(admin);
        when(roleMapper.selectIncludingDeleted(10L, ProjectRoleScopeEnum.GLOBAL.getValue(), 0L, "PARTICIPANT"))
                .thenReturn(participant);
        when(rolePermissionMapper.selectListByRoleId(1L)).thenReturn(List.of());
        when(rolePermissionMapper.selectListByRoleId(2L)).thenReturn(List.of(projectQuery, workItemQuery));
        when(rolePermissionMapper.selectIncludingDeleted(10L, 2L, "PROJECT_QUERY")).thenReturn(projectQuery);
        when(rolePermissionMapper.selectIncludingDeleted(10L, 2L, "WORK_ITEM_QUERY")).thenReturn(workItemQuery);
        when(roleMapper.selectListByScopeAndProjectId(ProjectRoleScopeEnum.GLOBAL.getValue(), 0L))
                .thenReturn(List.of(admin, participant, developer));
        when(rolePermissionMapper.selectListByRoleIds(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(projectQuery, workItemQuery, workItemUpdate));
        AtomicLong targetRoleId = new AtomicLong(100L);
        doAnswer(invocation -> {
            ProjectRoleDO role = invocation.getArgument(0);
            role.setId(targetRoleId.incrementAndGet());
            return 1;
        }).when(roleMapper).insert(any(ProjectRoleDO.class));

        Map<String, Long> result = service.snapshotGlobalRoles(99L);

        assertEquals(Map.of("ADMIN", 101L, "PARTICIPANT", 102L), result);
        verify(roleMapper, times(3)).insert(any(ProjectRoleDO.class));
        verify(rolePermissionMapper, times(3)).insert(any(ProjectRolePermissionDO.class));
    }

    private ProjectRoleAssignPermissionsReqVO assignReq(Set<String> codes) {
        ProjectRoleAssignPermissionsReqVO reqVO = new ProjectRoleAssignPermissionsReqVO();
        reqVO.setRoleId(1L);
        reqVO.setPermissionCodes(codes);
        return reqVO;
    }

    private ProjectRoleDO buildRole(String builtinType) {
        ProjectRoleDO role = new ProjectRoleDO();
        role.setId(1L);
        role.setScope(ProjectRoleScopeEnum.PROJECT.getValue());
        role.setProjectId(10L);
        role.setBuiltinType(builtinType);
        return role;
    }

    private ProjectRoleDO buildGlobalRole(Long id, String builtinType) {
        ProjectRoleDO role = new ProjectRoleDO();
        role.setId(id);
        role.setScope(ProjectRoleScopeEnum.GLOBAL.getValue());
        role.setProjectId(0L);
        role.setCode(builtinType);
        role.setName(builtinType);
        role.setBuiltinType(builtinType);
        role.setSort(0);
        return role;
    }

    private ProjectRolePermissionDO permission(Long roleId, String code) {
        ProjectRolePermissionDO permission = new ProjectRolePermissionDO();
        permission.setRoleId(roleId);
        permission.setPermissionCode(code);
        return permission;
    }

}
