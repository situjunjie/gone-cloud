package cn.iocoder.yudao.module.devops.service.application;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationMemberAddReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationMemberRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationMemberTransferOwnerReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationMemberDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMemberMapper;
import cn.iocoder.yudao.module.devops.enums.ApplicationMemberTypeEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_MEMBER_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_MEMBER_OWNER_CANNOT_REMOVE;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_MEMBER_OWNER_ONLY;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_MEMBER_TRANSFER_SELF;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_MEMBER_TRANSFER_TARGET_NOT_MEMBER;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.APPLICATION_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationMemberServiceImpl} 的单元测试。
 */
public class ApplicationMemberServiceImplTest extends BaseMockitoUnitTest {

    private static final Long TENANT_ID = 10L;
    private static final Long APP_ID = 100L;
    private static final Long OWNER_USER_ID = 1L;
    private static final Long OPERATOR_USER_ID = 1L;
    private static final LocalDateTime APP_CREATE_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime MEMBER_CREATE_TIME = LocalDateTime.of(2026, 2, 1, 0, 0);

    @InjectMocks
    private ApplicationMemberServiceImpl applicationMemberService;

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private ApplicationMemberMapper applicationMemberMapper;
    @Mock
    private AdminUserApi adminUserApi;

    // ========== 列表 ==========

    @Test
    public void testGetApplicationMemberList_ownerSynthesizedWhenNotMaterialized() {
        // 准备参数
        when(applicationMapper.selectById(APP_ID)).thenReturn(buildApplication(APP_ID, OWNER_USER_ID));
        when(applicationMemberMapper.selectListByAppId(APP_ID)).thenReturn(List.of(
                buildMember(200L, 2L, MEMBER_CREATE_TIME),
                buildMember(201L, 3L, MEMBER_CREATE_TIME.plusDays(1))));

        // 调用
        List<ApplicationMemberRespVO> result = applicationMemberService.getApplicationMemberList(APP_ID,
                OPERATOR_USER_ID);

        // 断言：拥有者未物化时实时合成一行，id 为空，排在首位
        assertEquals(3, result.size());
        ApplicationMemberRespVO owner = result.get(0);
        assertNull(owner.getId());
        assertEquals(OWNER_USER_ID, owner.getUserId());
        assertEquals(ApplicationMemberTypeEnum.OWNER.getType(), owner.getMemberType());
        assertEquals(APP_CREATE_TIME, owner.getCreateTime());
        assertEquals(2L, result.get(1).getUserId());
        assertEquals(ApplicationMemberTypeEnum.PARTICIPANT.getType(), result.get(1).getMemberType());
        assertEquals(3L, result.get(2).getUserId());
    }

    @Test
    public void testGetApplicationMemberList_ownerMaterializedStaysFirst() {
        // 准备参数：拥有者行加入时间晚于参与者，仍应排在首位
        when(applicationMapper.selectById(APP_ID)).thenReturn(buildApplication(APP_ID, OWNER_USER_ID));
        when(applicationMemberMapper.selectListByAppId(APP_ID)).thenReturn(List.of(
                buildMember(200L, 2L, MEMBER_CREATE_TIME),
                buildMember(199L, OWNER_USER_ID, MEMBER_CREATE_TIME.plusDays(1))));

        // 调用
        List<ApplicationMemberRespVO> result = applicationMemberService.getApplicationMemberList(APP_ID,
                OPERATOR_USER_ID);

        // 断言：复用拥有者的成员行，保留行 id
        assertEquals(2, result.size());
        assertEquals(199L, result.get(0).getId());
        assertEquals(OWNER_USER_ID, result.get(0).getUserId());
        assertEquals(ApplicationMemberTypeEnum.OWNER.getType(), result.get(0).getMemberType());
        assertEquals(2L, result.get(1).getUserId());
    }

    @Test
    public void testGetApplicationMemberList_applicationNotExists() {
        when(applicationMapper.selectById(APP_ID)).thenReturn(null);

        assertServiceException(() -> applicationMemberService.getApplicationMemberList(APP_ID, OPERATOR_USER_ID),
                APPLICATION_NOT_EXISTS);
    }

    @Test
    public void testGetApplicationMemberList_notOwner() {
        when(applicationMapper.selectById(APP_ID)).thenReturn(buildApplication(APP_ID, OWNER_USER_ID));

        assertServiceException(() -> applicationMemberService.getApplicationMemberList(APP_ID, 9L),
                APPLICATION_MEMBER_OWNER_ONLY);
    }

    // ========== 添加 ==========

    @Test
    public void testAddApplicationMembers_skipOwnerExistingAndRestoreDeleted() {
        // 准备参数：1 是拥有者，2 已存在，3 已逻辑删除，4 是新成员
        when(applicationMapper.selectById(APP_ID)).thenReturn(buildApplication(APP_ID, OWNER_USER_ID));
        when(adminUserApi.validateUserList(any())).thenReturn(CommonResult.success(true));
        ApplicationMemberDO deletedMember = buildMember(201L, 3L, MEMBER_CREATE_TIME);
        deletedMember.setDeleted(true);
        when(applicationMemberMapper.selectListByTenantIdAndAppIdAndUserIdsIncludingDeleted(eq(TENANT_ID),
                eq(APP_ID), any())).thenReturn(List.of(buildMember(200L, 2L, MEMBER_CREATE_TIME), deletedMember));
        ApplicationMemberAddReqVO reqVO = buildAddReqVO(List.of(1L, 2L, 3L, 4L, 4L));

        // 调用
        applicationMemberService.addApplicationMembers(reqVO, OPERATOR_USER_ID);

        // 断言：拥有者被剔除后校验用户，已删除的恢复，仅新成员插入
        ArgumentCaptor<Collection<Long>> userIdsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(adminUserApi).validateUserList(userIdsCaptor.capture());
        assertEquals(List.of(2L, 3L, 4L), List.copyOf(userIdsCaptor.getValue()));
        verify(applicationMemberMapper).restoreById(eq(201L), eq(TENANT_ID), any(LocalDateTime.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ApplicationMemberDO>> insertCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(applicationMemberMapper).insertBatch(insertCaptor.capture());
        List<ApplicationMemberDO> inserted = List.copyOf(insertCaptor.getValue());
        assertEquals(1, inserted.size());
        assertEquals(4L, inserted.get(0).getUserId());
        assertEquals(APP_ID, inserted.get(0).getAppId());
    }

    @Test
    public void testAddApplicationMembers_onlyOwner_noop() {
        when(applicationMapper.selectById(APP_ID)).thenReturn(buildApplication(APP_ID, OWNER_USER_ID));
        ApplicationMemberAddReqVO reqVO = buildAddReqVO(List.of(OWNER_USER_ID));

        applicationMemberService.addApplicationMembers(reqVO, OPERATOR_USER_ID);

        verify(adminUserApi, never()).validateUserList(any());
        verify(applicationMemberMapper, never()).insertBatch(any());
    }

    @Test
    public void testAddApplicationMembers_notOwner() {
        when(applicationMapper.selectById(APP_ID)).thenReturn(buildApplication(APP_ID, OWNER_USER_ID));

        assertServiceException(() -> applicationMemberService.addApplicationMembers(
                buildAddReqVO(List.of(2L)), 9L), APPLICATION_MEMBER_OWNER_ONLY);
        verify(applicationMemberMapper, never()).insertBatch(any());
    }

    // ========== 移除 ==========

    @Test
    public void testRemoveApplicationMember_ownerCannotRemove() {
        when(applicationMapper.selectById(APP_ID)).thenReturn(buildApplication(APP_ID, OWNER_USER_ID));

        assertServiceException(() -> applicationMemberService.removeApplicationMember(APP_ID, OWNER_USER_ID,
                OPERATOR_USER_ID), APPLICATION_MEMBER_OWNER_CANNOT_REMOVE);
        verify(applicationMemberMapper, never()).deleteById(any(Long.class));
    }

    @Test
    public void testRemoveApplicationMember_notExists() {
        when(applicationMapper.selectById(APP_ID)).thenReturn(buildApplication(APP_ID, OWNER_USER_ID));
        when(applicationMemberMapper.selectByAppIdAndUserId(APP_ID, 2L)).thenReturn(null);

        assertServiceException(() -> applicationMemberService.removeApplicationMember(APP_ID, 2L, OPERATOR_USER_ID),
                APPLICATION_MEMBER_NOT_EXISTS);
    }

    @Test
    public void testRemoveApplicationMember_success() {
        when(applicationMapper.selectById(APP_ID)).thenReturn(buildApplication(APP_ID, OWNER_USER_ID));
        when(applicationMemberMapper.selectByAppIdAndUserId(APP_ID, 2L))
                .thenReturn(buildMember(200L, 2L, MEMBER_CREATE_TIME));

        applicationMemberService.removeApplicationMember(APP_ID, 2L, OPERATOR_USER_ID);

        verify(applicationMemberMapper).deleteById(200L);
    }

    // ========== 转移拥有者 ==========

    @Test
    public void testTransferApplicationOwner_success() {
        // 准备参数：目标 2 是已有成员，原拥有者 1 未物化
        when(applicationMapper.selectById(APP_ID)).thenReturn(buildApplication(APP_ID, OWNER_USER_ID));
        when(applicationMemberMapper.selectByAppIdAndUserId(APP_ID, 2L))
                .thenReturn(buildMember(200L, 2L, MEMBER_CREATE_TIME));
        when(applicationMemberMapper.selectByTenantIdAndAppIdAndUserIdIncludingDeleted(TENANT_ID, APP_ID,
                OWNER_USER_ID)).thenReturn(null);

        // 调用
        applicationMemberService.transferApplicationOwner(buildTransferReqVO(2L), OPERATOR_USER_ID);

        // 断言：原拥有者补齐成员行（成为参与者），并同步 owner_user_id
        ArgumentCaptor<ApplicationMemberDO> memberCaptor = ArgumentCaptor.forClass(ApplicationMemberDO.class);
        verify(applicationMemberMapper).insert(memberCaptor.capture());
        assertEquals(APP_ID, memberCaptor.getValue().getAppId());
        assertEquals(OWNER_USER_ID, memberCaptor.getValue().getUserId());
        ArgumentCaptor<ApplicationDO> appCaptor = ArgumentCaptor.forClass(ApplicationDO.class);
        verify(applicationMapper).updateById(appCaptor.capture());
        assertEquals(APP_ID, appCaptor.getValue().getId());
        assertEquals(2L, appCaptor.getValue().getOwnerUserId());
    }

    @Test
    public void testTransferApplicationOwner_restoreDeletedOldOwnerRow() {
        when(applicationMapper.selectById(APP_ID)).thenReturn(buildApplication(APP_ID, OWNER_USER_ID));
        when(applicationMemberMapper.selectByAppIdAndUserId(APP_ID, 2L))
                .thenReturn(buildMember(200L, 2L, MEMBER_CREATE_TIME));
        ApplicationMemberDO deletedOldOwner = buildMember(199L, OWNER_USER_ID, MEMBER_CREATE_TIME);
        deletedOldOwner.setDeleted(true);
        when(applicationMemberMapper.selectByTenantIdAndAppIdAndUserIdIncludingDeleted(TENANT_ID, APP_ID,
                OWNER_USER_ID)).thenReturn(deletedOldOwner);

        applicationMemberService.transferApplicationOwner(buildTransferReqVO(2L), OPERATOR_USER_ID);

        verify(applicationMemberMapper).restoreById(eq(199L), eq(TENANT_ID), any(LocalDateTime.class));
        verify(applicationMemberMapper, never()).insert(any(ApplicationMemberDO.class));
        verify(applicationMapper).updateById(any(ApplicationDO.class));
    }

    @Test
    public void testTransferApplicationOwner_targetNotMember() {
        when(applicationMapper.selectById(APP_ID)).thenReturn(buildApplication(APP_ID, OWNER_USER_ID));
        when(applicationMemberMapper.selectByAppIdAndUserId(APP_ID, 2L)).thenReturn(null);

        assertServiceException(() -> applicationMemberService.transferApplicationOwner(buildTransferReqVO(2L),
                OPERATOR_USER_ID), APPLICATION_MEMBER_TRANSFER_TARGET_NOT_MEMBER);
        verify(applicationMapper, never()).updateById(any(ApplicationDO.class));
    }

    @Test
    public void testTransferApplicationOwner_self() {
        when(applicationMapper.selectById(APP_ID)).thenReturn(buildApplication(APP_ID, OWNER_USER_ID));

        assertServiceException(() -> applicationMemberService.transferApplicationOwner(
                buildTransferReqVO(OWNER_USER_ID), OPERATOR_USER_ID), APPLICATION_MEMBER_TRANSFER_SELF);
        verify(applicationMapper, never()).updateById(any(ApplicationDO.class));
    }

    @Test
    public void testTransferApplicationOwner_notOwner() {
        when(applicationMapper.selectById(APP_ID)).thenReturn(buildApplication(APP_ID, OWNER_USER_ID));

        assertServiceException(() -> applicationMemberService.transferApplicationOwner(buildTransferReqVO(2L), 9L),
                APPLICATION_MEMBER_OWNER_ONLY);
    }

    // ========== 辅助方法 ==========

    private ApplicationDO buildApplication(Long id, Long ownerUserId) {
        ApplicationDO application = new ApplicationDO();
        application.setId(id);
        application.setOwnerUserId(ownerUserId);
        application.setTenantId(TENANT_ID);
        application.setCreateTime(APP_CREATE_TIME);
        return application;
    }

    private ApplicationMemberDO buildMember(Long id, Long userId, LocalDateTime createTime) {
        ApplicationMemberDO member = new ApplicationMemberDO();
        member.setId(id);
        member.setAppId(APP_ID);
        member.setUserId(userId);
        member.setCreateTime(createTime);
        member.setDeleted(false);
        return member;
    }

    private ApplicationMemberAddReqVO buildAddReqVO(List<Long> userIds) {
        ApplicationMemberAddReqVO reqVO = new ApplicationMemberAddReqVO();
        reqVO.setAppId(APP_ID);
        reqVO.setUserIds(userIds);
        return reqVO;
    }

    private ApplicationMemberTransferOwnerReqVO buildTransferReqVO(Long userId) {
        ApplicationMemberTransferOwnerReqVO reqVO = new ApplicationMemberTransferOwnerReqVO();
        reqVO.setAppId(APP_ID);
        reqVO.setUserId(userId);
        return reqVO;
    }

}
