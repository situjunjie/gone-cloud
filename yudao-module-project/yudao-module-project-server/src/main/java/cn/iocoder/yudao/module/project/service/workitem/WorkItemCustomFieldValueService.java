package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemCustomFieldValueReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemCustomFieldValueRespVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface WorkItemCustomFieldValueService {

    void saveSnapshot(Long workItemId, Long projectId, String category,
                      List<WorkItemCustomFieldValueReqVO> values, boolean validateRequired);

    void updateValues(Long workItemId, Long projectId, String category,
                      List<WorkItemCustomFieldValueReqVO> values);

    void deleteByWorkItemId(Long workItemId);

    boolean hasValue(Long workItemId);

    void validateCategoryChange(Long workItemId, String newCategory);

    Map<Long, List<WorkItemCustomFieldValueRespVO>> getValueMap(Collection<Long> workItemIds);

}
