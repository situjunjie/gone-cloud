package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineJenkinsToolRespVO;
import cn.iocoder.yudao.module.devops.framework.jenkins.JenkinsPipelineClient;
import cn.iocoder.yudao.module.devops.framework.jenkins.JenkinsToolInstallation;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Jenkins 工具配置查询服务。
 */
@Service
public class PipelineJenkinsToolServiceImpl implements PipelineJenkinsToolService {

    @Resource
    private JenkinsPipelineClient jenkinsPipelineClient;

    @Override
    public List<PipelineJenkinsToolRespVO> getJenkinsTools(String type) {
        return jenkinsPipelineClient.getToolInstallations(type).stream()
                .map(this::convert)
                .toList();
    }

    private PipelineJenkinsToolRespVO convert(JenkinsToolInstallation installation) {
        PipelineJenkinsToolRespVO respVO = new PipelineJenkinsToolRespVO();
        respVO.setType(installation.getType());
        respVO.setName(installation.getName());
        respVO.setHome(installation.getHome());
        return respVO;
    }

}
