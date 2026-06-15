package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationRespVO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;

import java.util.List;

public interface PipelineSpecValidationService {

    PipelineValidationRespVO validate(String specJson);

    PipelineValidationRespVO validate(PipelineSpec spec);

    PipelineSpec parseSpec(String specJson, PipelineValidationRespVO validation);

    List<PipelineSpec.ExecutableStep> sortExecutableSteps(PipelineSpec spec);

}
