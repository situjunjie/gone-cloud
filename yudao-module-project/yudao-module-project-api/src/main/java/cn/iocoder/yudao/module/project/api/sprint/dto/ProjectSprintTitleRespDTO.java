package cn.iocoder.yudao.module.project.api.sprint.dto;

import lombok.Data;
import org.dromara.core.trans.vo.VO;

@Data
public class ProjectSprintTitleRespDTO implements VO {

    private Long id;

    private String title;

}
