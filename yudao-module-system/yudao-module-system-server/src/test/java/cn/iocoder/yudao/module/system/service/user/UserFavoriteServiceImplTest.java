package cn.iocoder.yudao.module.system.service.user;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.favorite.UserFavoriteCreateReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.UserFavoriteDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.UserFavoriteMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(UserFavoriteServiceImpl.class)
public class UserFavoriteServiceImplTest extends BaseDbUnitTest {

    private static final Long USER_ID = 1L;
    private static final Long TENANT_ID = 1L;

    @Resource
    private UserFavoriteServiceImpl userFavoriteService;
    @Resource
    private UserFavoriteMapper userFavoriteMapper;

    @Test
    public void testCreateUserFavorite_success() {
        UserFavoriteCreateReqVO reqVO = buildCreateReqVO("DEVOPS_APPLICATION", 100L);

        userFavoriteService.createUserFavorite(USER_ID, TENANT_ID, reqVO);

        List<UserFavoriteDO> favorites = userFavoriteMapper.selectListByTenantIdAndUserIdAndBizType(
                TENANT_ID, USER_ID, reqVO.getBizType());
        assertEquals(1, favorites.size());
        assertPojoEquals(reqVO, favorites.get(0));
        assertEquals(USER_ID, favorites.get(0).getUserId());
        assertEquals(TENANT_ID, favorites.get(0).getTenantId());
    }

    @Test
    public void testCreateUserFavorite_idempotentWhenActiveExists() {
        UserFavoriteDO favorite = createFavorite("DEVOPS_APPLICATION", 100L, false);
        userFavoriteMapper.insert(favorite);

        userFavoriteService.createUserFavorite(USER_ID, TENANT_ID, buildCreateReqVO("DEVOPS_APPLICATION", 100L));

        List<UserFavoriteDO> favorites = userFavoriteMapper.selectListByTenantIdAndUserIdAndBizType(
                TENANT_ID, USER_ID, "DEVOPS_APPLICATION");
        assertEquals(1, favorites.size());
        assertEquals(favorite.getId(), favorites.get(0).getId());
    }

    @Test
    public void testCreateUserFavorite_restoreDeletedRecord() {
        UserFavoriteDO favorite = createFavorite("DEVOPS_ENVIRONMENT", 200L, false);
        userFavoriteMapper.insert(favorite);
        userFavoriteService.cancelUserFavorite(USER_ID, TENANT_ID, favorite.getBizType(), favorite.getBizId());
        assertNullActiveFavorite("DEVOPS_ENVIRONMENT", 200L);

        userFavoriteService.createUserFavorite(USER_ID, TENANT_ID, buildCreateReqVO("DEVOPS_ENVIRONMENT", 200L));

        UserFavoriteDO restored = userFavoriteMapper.selectByTenantIdAndUserIdAndBizTypeAndBizId(
                TENANT_ID, USER_ID, "DEVOPS_ENVIRONMENT", 200L);
        assertNotNull(restored);
        assertEquals(favorite.getId(), restored.getId());
        assertFalse(Boolean.TRUE.equals(restored.getDeleted()));
    }

    @Test
    public void testCancelUserFavorite_success() {
        UserFavoriteDO favorite = createFavorite("DEVOPS_APPLICATION", 100L, false);
        userFavoriteMapper.insert(favorite);

        userFavoriteService.cancelUserFavorite(USER_ID, TENANT_ID, favorite.getBizType(), favorite.getBizId());

        assertNullActiveFavorite("DEVOPS_APPLICATION", 100L);
        UserFavoriteDO deletedFavorite = userFavoriteMapper.selectByTenantIdAndUserIdAndBizTypeAndBizIdIncludingDeleted(
                TENANT_ID, USER_ID, "DEVOPS_APPLICATION", 100L);
        assertNotNull(deletedFavorite);
        assertTrue(Boolean.TRUE.equals(deletedFavorite.getDeleted()));
    }

    @Test
    public void testGetUserFavoriteList_success() {
        userFavoriteMapper.insert(createFavorite("DEVOPS_APPLICATION", 100L, false));
        userFavoriteMapper.insert(createFavorite("DEVOPS_APPLICATION", 101L, false));
        userFavoriteMapper.insert(createFavorite("DEVOPS_ENVIRONMENT", 200L, false));
        UserFavoriteDO otherTenantFavorite = new UserFavoriteDO();
        otherTenantFavorite.setUserId(USER_ID);
        otherTenantFavorite.setTenantId(2L);
        otherTenantFavorite.setBizType("DEVOPS_APPLICATION");
        otherTenantFavorite.setBizId(300L);
        userFavoriteMapper.insert(otherTenantFavorite);

        List<UserFavoriteDO> favorites = userFavoriteService.getUserFavoriteList(USER_ID, TENANT_ID, "DEVOPS_APPLICATION");

        assertEquals(2, favorites.size());
        assertTrue(favorites.stream().allMatch(item -> "DEVOPS_APPLICATION".equals(item.getBizType())));
        assertTrue(favorites.stream().allMatch(item -> TENANT_ID.equals(item.getTenantId())));
    }

    private UserFavoriteCreateReqVO buildCreateReqVO(String bizType, Long bizId) {
        UserFavoriteCreateReqVO reqVO = new UserFavoriteCreateReqVO();
        reqVO.setBizType(bizType);
        reqVO.setBizId(bizId);
        return reqVO;
    }

    private UserFavoriteDO createFavorite(String bizType, Long bizId, boolean deleted) {
        UserFavoriteDO favorite = new UserFavoriteDO();
        favorite.setUserId(USER_ID);
        favorite.setTenantId(TENANT_ID);
        favorite.setBizType(bizType);
        favorite.setBizId(bizId);
        favorite.setDeleted(deleted);
        return favorite;
    }

    private void assertNullActiveFavorite(String bizType, Long bizId) {
        UserFavoriteDO favorite = userFavoriteMapper.selectByTenantIdAndUserIdAndBizTypeAndBizId(
                TENANT_ID, USER_ID, bizType, bizId);
        assertNull(favorite);
    }

}
