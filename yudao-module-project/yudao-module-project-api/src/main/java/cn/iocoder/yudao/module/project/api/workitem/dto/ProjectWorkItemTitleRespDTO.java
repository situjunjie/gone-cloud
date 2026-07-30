package cn.iocoder.yudao.module.project.api.workitem.dto;

import lombok.Data;
import org.dromara.core.trans.vo.VO;

@Data
public class ProjectWorkItemTitleRespDTO implements VO {

    private Long id;

    private String title;

}
