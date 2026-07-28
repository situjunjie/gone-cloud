package cn.iocoder.yudao.module.project.service.worklog;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogRespVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogSummaryReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogSummaryRespVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogUpdateReqVO;

public interface WorklogService {

    Long createWorklog(WorklogCreateReqVO createReqVO);

    void updateWorklog(WorklogUpdateReqVO updateReqVO);

    void deleteWorklog(Long id);

    WorklogRespVO getWorklog(Long id);

    PageResult<WorklogRespVO> getWorklogPage(WorklogPageReqVO pageReqVO);

    WorklogSummaryRespVO getWorklogSummary(WorklogSummaryReqVO reqVO);

    boolean hasWorklog(Long workItemId);

    void deleteByWorkItemId(Long workItemId);

}
