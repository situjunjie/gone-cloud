package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;

public interface PipelinePlatformNodeAdvanceService {

    void advance(PipelineRunDO run, PipelineDefinitionVersionDO version);

    void advance(PipelineRunDO run);

}
