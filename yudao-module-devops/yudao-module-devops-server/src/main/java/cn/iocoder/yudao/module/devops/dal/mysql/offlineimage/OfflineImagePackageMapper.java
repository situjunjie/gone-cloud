package cn.iocoder.yudao.module.devops.dal.mysql.offlineimage;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.controller.admin.offlineimage.vo.OfflineImagePackagePageReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.offlineimage.OfflineImagePackageDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OfflineImagePackageMapper extends BaseMapperX<OfflineImagePackageDO> {

    default OfflineImagePackageDO selectByPipelineRunIdAndImage(Long pipelineRunId, String imageName, String imageTag) {
        return selectOne(new LambdaQueryWrapperX<OfflineImagePackageDO>()
                .eq(OfflineImagePackageDO::getPipelineRunId, pipelineRunId)
                .eq(OfflineImagePackageDO::getImageName, imageName)
                .eq(OfflineImagePackageDO::getImageTag, imageTag)
                .last("LIMIT 1"));
    }

    default PageResult<OfflineImagePackageDO> selectPage(OfflineImagePackagePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<OfflineImagePackageDO>()
                .eqIfPresent(OfflineImagePackageDO::getPipelineRunId, reqVO.getPipelineRunId())
                .likeIfPresent(OfflineImagePackageDO::getImageName, reqVO.getImageName())
                .eqIfPresent(OfflineImagePackageDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(OfflineImagePackageDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(OfflineImagePackageDO::getCreateTime)
                .orderByDesc(OfflineImagePackageDO::getId));
    }

}
