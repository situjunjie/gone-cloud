package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldOptionSaveReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldSaveReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldDO;

import java.util.List;

public interface WorkItemFieldService {

    Long createField(WorkItemFieldSaveReqVO reqVO);

    void updateField(WorkItemFieldSaveReqVO reqVO);

    void updateFieldEnabled(Long id, Boolean enabled);

    List<WorkItemFieldRespVO> getFieldList(Long projectId);

    Long createOption(WorkItemFieldOptionSaveReqVO reqVO);

    void updateOption(WorkItemFieldOptionSaveReqVO reqVO);

    void updateOptionEnabled(Long id, Boolean enabled);

    WorkItemFieldDO validateFieldExists(Long id);

}
