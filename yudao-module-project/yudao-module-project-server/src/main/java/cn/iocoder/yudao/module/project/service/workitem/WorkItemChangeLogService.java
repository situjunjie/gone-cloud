package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemChangeLogPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemChangeLogRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemCustomFieldValueRespVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;

import java.util.List;
import java.util.Map;

public interface WorkItemChangeLogService {

    void initContext();

    Snapshot capture(ProjectWorkItemDO workItem, boolean includeCustomFields);

    void record(Snapshot before, Snapshot after);

    PageResult<WorkItemChangeLogRespVO> getPage(WorkItemChangeLogPageReqVO reqVO);

    PageResult<WorkItemChangeLogRespVO> getTimelinePage(WorkItemChangeLogPageReqVO reqVO);

    record Snapshot(ProjectWorkItemDO workItem, Map<Long, WorkItemCustomFieldValueRespVO> customFields,
                    boolean customFieldsAvailable) {

        public Snapshot(ProjectWorkItemDO workItem, Map<Long, WorkItemCustomFieldValueRespVO> customFields) {
            this(workItem, customFields, true);
        }

    }

}
