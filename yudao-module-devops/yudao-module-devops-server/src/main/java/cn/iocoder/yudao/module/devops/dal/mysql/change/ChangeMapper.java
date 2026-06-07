package cn.iocoder.yudao.module.devops.dal.mysql.change;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangePageReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ChangeMapper extends BaseMapperX<ChangeDO> {

    default ChangeDO selectByAppIdAndChangeKey(Long appId, String changeKey) {
        return selectOne(ChangeDO::getAppId, appId, ChangeDO::getChangeKey, changeKey);
    }

    default ChangeDO selectByAppIdAndBranchName(Long appId, String branchName) {
        return selectOne(ChangeDO::getAppId, appId, ChangeDO::getBranchName, branchName);
    }

    default List<ChangeDO> selectListByAppIdAndStatus(Long appId, Integer status) {
        return selectList(new LambdaQueryWrapperX<ChangeDO>()
                .eq(ChangeDO::getAppId, appId)
                .eq(ChangeDO::getStatus, status)
                .orderByDesc(ChangeDO::getId));
    }

    default List<ChangeDO> selectListByIds(Collection<Long> ids) {
        return selectList(new LambdaQueryWrapperX<ChangeDO>()
                .in(ChangeDO::getId, ids));
    }

    default PageResult<ChangeDO> selectPage(ChangePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ChangeDO>()
                .eqIfPresent(ChangeDO::getAppId, reqVO.getAppId())
                .likeIfPresent(ChangeDO::getChangeKey, reqVO.getChangeKey())
                .likeIfPresent(ChangeDO::getTitle, reqVO.getTitle())
                .likeIfPresent(ChangeDO::getBranchName, reqVO.getBranchName())
                .eqIfPresent(ChangeDO::getOwnerUserId, reqVO.getOwnerUserId())
                .eqIfPresent(ChangeDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(ChangeDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ChangeDO::getId));
    }

}
