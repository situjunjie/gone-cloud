package cn.iocoder.yudao.module.devops.service.change;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeDiscardReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeEnvMountReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeEnvRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeEnvUnmountReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangePageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.change.vo.ChangeSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.change.ChangeDO;

import java.util.List;

public interface ChangeService {

    Long createChange(ChangeSaveReqVO createReqVO);

    void updateChange(ChangeSaveReqVO updateReqVO);

    void deleteChange(Long id);

    void releaseChange(Long id);

    void discardChange(ChangeDiscardReqVO discardReqVO);

    ChangeDO getChange(Long id);

    PageResult<ChangeDO> getChangePage(ChangePageReqVO pageReqVO);

    Long mountChangeEnv(ChangeEnvMountReqVO mountReqVO, Long userId);

    void unmountChangeEnv(ChangeEnvUnmountReqVO unmountReqVO, Long userId);

    List<ChangeEnvRespVO> getChangeEnvList(Long changeId);

}
