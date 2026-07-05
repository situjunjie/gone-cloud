package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemQueryViewSaveReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemQueryViewDO;

import java.util.List;

public interface WorkItemQueryViewService {

    Long createQueryView(WorkItemQueryViewSaveReqVO createReqVO, Long loginUserId);

    void updateQueryView(WorkItemQueryViewSaveReqVO updateReqVO, Long loginUserId);

    void deleteQueryView(Long id, Long loginUserId);

    WorkItemQueryViewDO getQueryView(Long id, Long loginUserId);

    WorkItemQueryViewDO getQueryViewByCode(String scope, String code, Long loginUserId);

    List<WorkItemQueryViewDO> getQueryViewList(Long loginUserId);

}
