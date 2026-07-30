package cn.iocoder.yudao.module.project.api.workitem;

import cn.iocoder.yudao.module.project.api.workitem.dto.ProjectWorkItemTitleRespDTO;
import cn.iocoder.yudao.module.project.enums.ApiConstants;
import org.dromara.core.trans.anno.AutoTrans;
import org.dromara.trans.service.AutoTransable;

import static cn.iocoder.yudao.module.project.api.workitem.ProjectWorkItemApi.PREFIX;

@AutoTrans(namespace = PREFIX, fields = "title")
public interface ProjectWorkItemApi extends AutoTransable<ProjectWorkItemTitleRespDTO> {

    String PREFIX = ApiConstants.PREFIX + "/work-item";

}
