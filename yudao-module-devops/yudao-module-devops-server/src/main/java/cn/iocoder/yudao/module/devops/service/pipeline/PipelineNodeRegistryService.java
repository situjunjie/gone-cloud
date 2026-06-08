package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineCommandTemplateRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineNodeTypeRespVO;

import java.util.List;

public interface PipelineNodeRegistryService {

    List<PipelineNodeTypeRespVO> getNodeTypes();

    List<PipelineNodeTypeRespVO> getConfigurableNodeTypes();

    List<PipelineCommandTemplateRespVO> getCommandTemplates();

    PipelineNodeTypeRespVO getNodeType(String type);

    PipelineCommandTemplateRespVO getCommandTemplate(String templateKey);

}
