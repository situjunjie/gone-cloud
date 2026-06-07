package cn.iocoder.yudao.module.devops.dal.mysql.change;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangePageReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeDO;
import cn.iocoder.yudao.module.devops.enums.ChangeCodeReviewStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
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

    default ChangeDO selectByAppIdAndBranchNameAndStatus(Long appId, String branchName, Integer status) {
        return selectOne(ChangeDO::getAppId, appId,
                ChangeDO::getBranchName, branchName,
                ChangeDO::getStatus, status);
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

    default int updateTesterById(Long id, Long testerUserId, LocalDateTime updateTime) {
        return update(null, new LambdaUpdateWrapper<ChangeDO>()
                .eq(ChangeDO::getId, id)
                .set(ChangeDO::getTesterUserId, testerUserId)
                .set(ChangeDO::getUpdateTime, updateTime));
    }

    default int updateCodeReviewerById(Long id, Long codeReviewerUserId, LocalDateTime updateTime) {
        return update(null, new LambdaUpdateWrapper<ChangeDO>()
                .eq(ChangeDO::getId, id)
                .set(ChangeDO::getCodeReviewerUserId, codeReviewerUserId)
                .set(ChangeDO::getUpdateTime, updateTime));
    }

    default int updateCodeReviewInProgressById(Long id, Long codeReviewerUserId, LocalDateTime updateTime) {
        return update(null, new LambdaUpdateWrapper<ChangeDO>()
                .eq(ChangeDO::getId, id)
                .set(ChangeDO::getCodeReviewerUserId, codeReviewerUserId)
                .set(ChangeDO::getCodeReviewStatus, ChangeCodeReviewStatusEnum.IN_PROGRESS.getStatus())
                .set(ChangeDO::getUpdateTime, updateTime));
    }

    default int updateCodeReviewApprovedById(Long id, Long codeReviewerUserId, String commitSha, LocalDateTime updateTime) {
        return update(null, new LambdaUpdateWrapper<ChangeDO>()
                .eq(ChangeDO::getId, id)
                .set(ChangeDO::getCodeReviewerUserId, codeReviewerUserId)
                .set(ChangeDO::getCodeReviewStatus, ChangeCodeReviewStatusEnum.APPROVED.getStatus())
                .set(ChangeDO::getCodeReviewPassedCommitSha, commitSha)
                .set(ChangeDO::getUpdateTime, updateTime));
    }

    default int updateLatestCommitAndResetReviewTest(Long id, String commitSha, String commitMessage,
                                                     LocalDateTime commitAt, LocalDateTime updateTime) {
        return update(null, new LambdaUpdateWrapper<ChangeDO>()
                .eq(ChangeDO::getId, id)
                .set(ChangeDO::getLatestCommitSha, commitSha)
                .set(ChangeDO::getLatestCommitMessage, commitMessage)
                .set(ChangeDO::getLatestCommitAt, commitAt)
                .set(ChangeDO::getTestPassed, 0)
                .set(ChangeDO::getTestPassedCommitSha, null)
                .set(ChangeDO::getCodeReviewStatus, ChangeCodeReviewStatusEnum.OPEN.getStatus())
                .set(ChangeDO::getCodeReviewPassedCommitSha, null)
                .set(ChangeDO::getUpdateTime, updateTime));
    }

    default PageResult<ChangeDO> selectPage(ChangePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ChangeDO>()
                .eqIfPresent(ChangeDO::getAppId, reqVO.getAppId())
                .likeIfPresent(ChangeDO::getChangeKey, reqVO.getChangeKey())
                .likeIfPresent(ChangeDO::getTitle, reqVO.getTitle())
                .likeIfPresent(ChangeDO::getBranchName, reqVO.getBranchName())
                .eqIfPresent(ChangeDO::getOwnerUserId, reqVO.getOwnerUserId())
                .eqIfPresent(ChangeDO::getTesterUserId, reqVO.getTesterUserId())
                .eqIfPresent(ChangeDO::getTestPassed, reqVO.getTestPassed())
                .eqIfPresent(ChangeDO::getCodeReviewerUserId, reqVO.getCodeReviewerUserId())
                .eqIfPresent(ChangeDO::getCodeReviewStatus, reqVO.getCodeReviewStatus())
                .eqIfPresent(ChangeDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(ChangeDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ChangeDO::getId));
    }

}
