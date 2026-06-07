package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;

public interface JenkinsfileGeneratorService {

    String generate(PipelineSpec spec);

    String checksum(String jenkinsfileText);

}
