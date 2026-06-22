package cn.iocoder.yudao.module.system.service.user;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.favorite.UserFavoriteCreateReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.UserFavoriteDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.UserFavoriteMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台用户收藏 Service 实现类
 */
@Service
public class UserFavoriteServiceImpl implements UserFavoriteService {

    @Resource
    private UserFavoriteMapper userFavoriteMapper;

    @Override
    public List<UserFavoriteDO> getUserFavoriteList(Long userId, Long tenantId, String bizType) {
        return userFavoriteMapper.selectListByTenantIdAndUserIdAndBizType(tenantId, userId, bizType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createUserFavorite(Long userId, Long tenantId, UserFavoriteCreateReqVO createReqVO) {
        UserFavoriteDO favorite = userFavoriteMapper.selectByTenantIdAndUserIdAndBizTypeAndBizId(
                tenantId, userId, createReqVO.getBizType(), createReqVO.getBizId());
        if (favorite != null) {
            return;
        }
        UserFavoriteDO deletedFavorite = userFavoriteMapper.selectByTenantIdAndUserIdAndBizTypeAndBizIdIncludingDeleted(
                tenantId, userId, createReqVO.getBizType(), createReqVO.getBizId());
        if (deletedFavorite != null) {
            userFavoriteMapper.restoreById(deletedFavorite.getId(), LocalDateTime.now());
            return;
        }
        favorite = BeanUtils.toBean(createReqVO, UserFavoriteDO.class, bean -> {
            bean.setUserId(userId);
            bean.setTenantId(tenantId);
        });
        userFavoriteMapper.insert(favorite);
    }

    @Override
    public void cancelUserFavorite(Long userId, Long tenantId, String bizType, Long bizId) {
        userFavoriteMapper.deleteByTenantIdAndUserIdAndBizTypeAndBizId(tenantId, userId, bizType, bizId);
    }

}
