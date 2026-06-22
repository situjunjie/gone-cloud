package cn.iocoder.yudao.module.system.service.user;

import cn.iocoder.yudao.module.system.controller.admin.user.vo.favorite.UserFavoriteCreateReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.UserFavoriteDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 管理后台用户收藏 Service 接口
 */
public interface UserFavoriteService {

    /**
     * 获得用户指定业务类型的收藏列表
     *
     * @param userId   用户编号
     * @param tenantId 租户编号
     * @param bizType  业务类型
     * @return 收藏列表
     */
    List<UserFavoriteDO> getUserFavoriteList(Long userId, Long tenantId, String bizType);

    /**
     * 添加收藏
     *
     * @param userId      用户编号
     * @param tenantId    租户编号
     * @param createReqVO 收藏信息
     */
    void createUserFavorite(Long userId, Long tenantId, @Valid UserFavoriteCreateReqVO createReqVO);

    /**
     * 取消收藏
     *
     * @param userId   用户编号
     * @param tenantId 租户编号
     * @param bizType  业务类型
     * @param bizId    业务对象编号
     */
    void cancelUserFavorite(Long userId, Long tenantId, String bizType, Long bizId);

}
