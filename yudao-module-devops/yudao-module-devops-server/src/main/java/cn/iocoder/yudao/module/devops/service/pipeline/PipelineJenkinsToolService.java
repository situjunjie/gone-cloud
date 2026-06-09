package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineJenkinsToolRespVO;

import java.util.List;

public interface PipelineJenkinsToolService {

    List<PipelineJenkinsToolRespVO> getJenkinsTools(String type);

}
