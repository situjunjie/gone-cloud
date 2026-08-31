package cn.iocoder.yudao.module.devops.dal.mysql.application;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationPageReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface ApplicationMapper extends BaseMapperX<ApplicationDO> {

    default ApplicationDO selectByAppKey(String appKey) {
        return selectOne(ApplicationDO::getAppKey, appKey);
    }

    default ApplicationDO selectByRepositoryProviderIdAndRepoIdentifier(Long repositoryProviderId, String repoIdentifier) {
        return selectOne(new LambdaQueryWrapperX<ApplicationDO>()
                .eq(ApplicationDO::getRepositoryProviderId, repositoryProviderId)
                .eq(ApplicationDO::getRepoIdentifier, repoIdentifier));
    }

    default Long selectCountByRepositoryProviderId(Long repositoryProviderId) {
        return selectCount(ApplicationDO::getRepositoryProviderId, repositoryProviderId);
    }

    default int updateBaseBranchLatestCommit(Long id, String commitSha, String commitMessage,
                                             LocalDateTime commitAt, LocalDateTime updateTime) {
        return update(null, new LambdaUpdateWrapper<ApplicationDO>()
                .eq(ApplicationDO::getId, id)
                .set(ApplicationDO::getBaseBranchLatestCommitSha, commitSha)
                .set(ApplicationDO::getBaseBranchLatestCommitMessage, commitMessage)
                .set(ApplicationDO::getBaseBranchLatestCommitAt, commitAt)
                .set(ApplicationDO::getUpdateTime, updateTime));
    }

    default PageResult<ApplicationDO> selectPage(ApplicationPageReqVO reqVO) {
        LambdaQueryWrapperX<ApplicationDO> query = new LambdaQueryWrapperX<ApplicationDO>()
                .likeIfPresent(ApplicationDO::getAppKey, reqVO.getAppKey())
                .likeIfPresent(ApplicationDO::getName, reqVO.getName())
                .eqIfPresent(ApplicationDO::getRepositoryProviderId, reqVO.getRepositoryProviderId())
                .eqIfPresent(ApplicationDO::getRepoProviderType, reqVO.getRepoProviderType())
                .eqIfPresent(ApplicationDO::getOwnerUserId, reqVO.getOwnerUserId())
                .eqIfPresent(ApplicationDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(ApplicationDO::getCreateTime, reqVO.getCreateTime());
        if (reqVO.getFavori() != null) {
            if (reqVO.getFavori()) {
                if (reqVO.getFavoriIds() == null || reqVO.getFavoriIds().isEmpty()) {
                    query.apply("1 = 0");
                } else {
                    query.in(ApplicationDO::getId, reqVO.getFavoriIds());
                }
            } else if (reqVO.getFavoriIds() != null && !reqVO.getFavoriIds().isEmpty()) {
                query.notIn(ApplicationDO::getId, reqVO.getFavoriIds());
            }
        }
        return selectPage(reqVO, query.orderByDesc(ApplicationDO::getId));
    }

}
