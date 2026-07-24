package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemActivityPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemActivityRespVO;
import jakarta.validation.Valid;

public interface WorkItemActivityService {

    PageResult<WorkItemActivityRespVO> getPage(@Valid WorkItemActivityPageReqVO reqVO);

}
