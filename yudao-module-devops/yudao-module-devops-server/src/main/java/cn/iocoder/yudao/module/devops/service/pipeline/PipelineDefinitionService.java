package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineDefinitionRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineDefinitionVersionRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelinePublishReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineRollbackReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineSaveDraftReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidateReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationRespVO;

import java.util.List;

public interface PipelineDefinitionService {

    PipelineDefinitionRespVO getByApplicationEnvId(Long applicationEnvId);

    Long saveDraft(PipelineSaveDraftReqVO reqVO);

    PipelineValidationRespVO validate(PipelineValidateReqVO reqVO);

    Long publish(PipelinePublishReqVO reqVO, Long userId);

    Long rollback(PipelineRollbackReqVO reqVO, Long userId);

    List<PipelineDefinitionVersionRespVO> getVersionList(Long definitionId);

}
