package cn.iocoder.yudao.module.project.api.sprint;

import cn.iocoder.yudao.module.project.api.sprint.dto.ProjectSprintTitleRespDTO;
import cn.iocoder.yudao.module.project.enums.ApiConstants;
import org.dromara.core.trans.anno.AutoTrans;
import org.dromara.trans.service.AutoTransable;

import static cn.iocoder.yudao.module.project.api.sprint.ProjectSprintApi.PREFIX;

@AutoTrans(namespace = PREFIX, fields = "title")
public interface ProjectSprintApi extends AutoTransable<ProjectSprintTitleRespDTO> {

    String PREFIX = ApiConstants.PREFIX + "/sprint";

}
