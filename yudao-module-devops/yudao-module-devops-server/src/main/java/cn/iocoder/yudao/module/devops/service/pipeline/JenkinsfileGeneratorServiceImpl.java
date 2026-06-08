package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineCommandTemplateRespVO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * DevOps Jenkinsfile 生成服务。
 */
@Service
public class JenkinsfileGeneratorServiceImpl implements JenkinsfileGeneratorService {

    private static final String PARAM_COMMAND_TEMPLATE_KEY = "commandTemplateKey";

    @Resource
    private PipelineNodeRegistryService pipelineNodeRegistryService;
    @Resource
    private PipelineSpecValidationService pipelineSpecValidationService;

    @Override
    public String generate(PipelineSpec spec) {
        List<PipelineSpec.Node> nodes = pipelineSpecValidationService.sortNodes(spec);
        StringBuilder builder = new StringBuilder();
        builder.append("@Library('gone-devops-shared') _\n\n");
        builder.append("pipeline {\n");
        builder.append("  agent any\n");
        builder.append("  options {\n");
        builder.append("    timestamps()\n");
        builder.append("    disableConcurrentBuilds()\n");
        builder.append("  }\n");
        builder.append("  parameters {\n");
        builder.append("    string(name: 'PIPELINE_RUN_ID')\n");
        builder.append("    string(name: 'PIPELINE_VERSION_ID')\n");
        builder.append("    string(name: 'REPO_URL')\n");
        builder.append("    string(name: 'BRANCH_NAME')\n");
        builder.append("    string(name: 'COMMIT_SHA', defaultValue: '')\n");
        builder.append("    string(name: 'APP_KEY')\n");
        builder.append("    string(name: 'CALLBACK_URL')\n");
        builder.append("    password(name: 'CALLBACK_TOKEN')\n");
        builder.append("  }\n");
        builder.append("  stages {\n");
        for (PipelineSpec.Node node : nodes) {
            appendStage(builder, node);
        }
        builder.append("  }\n");
        builder.append("}\n");
        return builder.toString();
    }

    @Override
    public String checksum(String jenkinsfileText) {
        return DigestUtil.sha256Hex(jenkinsfileText);
    }

    private void appendStage(StringBuilder builder, PipelineSpec.Node node) {
        builder.append("    stage('").append(escapeGroovy(node.getId())).append("__")
                .append(escapeGroovy(node.getType())).append("') {\n");
        builder.append("      steps {\n");
        builder.append("        script {\n");
        appendCallback(builder, node, "STARTED", null);
        builder.append("          try {\n");
        switch (node.getType()) {
            case PipelineNodeRegistryServiceImpl.TYPE_CHECKOUT -> appendCheckout(builder);
            case PipelineNodeRegistryServiceImpl.TYPE_UNIT_TEST -> appendUnitTest(builder, node);
            case PipelineNodeRegistryServiceImpl.TYPE_BUILD_ARTIFACT -> appendBuildArtifact(builder, node);
            case PipelineNodeRegistryServiceImpl.TYPE_BUILD_IMAGE -> appendBuildImage(builder, node);
            case PipelineNodeRegistryServiceImpl.TYPE_REPORT_ARTIFACTS -> appendReportArtifacts(builder);
            case PipelineNodeRegistryServiceImpl.TYPE_MOCK -> appendMock(builder, node);
            default -> builder.append("            echo 'Unsupported node type: ")
                    .append(escapeGroovy(node.getType())).append("'\n");
        }
        appendCallback(builder, node, "COMPLETED", null);
        builder.append("          } catch (err) {\n");
        appendCallback(builder, node, "FAILED", "err.getMessage()");
        builder.append("            throw err\n");
        builder.append("          }\n");
        builder.append("        }\n");
        builder.append("      }\n");
        if (PipelineNodeRegistryServiceImpl.TYPE_UNIT_TEST.equals(node.getType())) {
            builder.append("      post {\n");
            builder.append("        always {\n");
            builder.append("          junit allowEmptyResults: true, testResults: '")
                    .append(escapeGroovy(param(node, "reportPattern", "**/surefire-reports/*.xml"))).append("'\n");
            builder.append("        }\n");
            builder.append("      }\n");
        }
        builder.append("    }\n");
    }

    private void appendCheckout(StringBuilder builder) {
        builder.append("            goneDevopsCheckout(repoUrl: params.REPO_URL, branchName: params.BRANCH_NAME, commitSha: params.COMMIT_SHA)\n");
    }

    private void appendUnitTest(StringBuilder builder, PipelineSpec.Node node) {
        builder.append("            goneDevopsUnitTest(command: '").append(escapeGroovy(command(node))).append("')\n");
    }

    private void appendBuildArtifact(StringBuilder builder, PipelineSpec.Node node) {
        builder.append("            goneDevopsBuildArtifact(command: '").append(escapeGroovy(command(node))).append("')\n");
        builder.append("            archiveArtifacts artifacts: '")
                .append(escapeGroovy(param(node, "artifactPattern", "**/target/*.jar")))
                .append("', fingerprint: true\n");
    }

    private void appendBuildImage(StringBuilder builder, PipelineSpec.Node node) {
        builder.append("            env.IMAGE_TAG = goneDevopsBuildImage(imageName: params.APP_KEY, imageTag: params.COMMIT_SHA ?: env.BUILD_NUMBER)\n");
    }

    private void appendReportArtifacts(StringBuilder builder) {
        builder.append("            goneDevopsReportArtifacts(pipelineRunId: params.PIPELINE_RUN_ID, imageTag: env.IMAGE_TAG)\n");
    }

    private void appendMock(StringBuilder builder, PipelineSpec.Node node) {
        builder.append("            echo 'MOCK node: ").append(escapeGroovy(param(node, "message", node.getName()))).append("'\n");
    }

    private void appendCallback(StringBuilder builder, PipelineSpec.Node node, String action, String messageExpression) {
        builder.append("            goneDevopsCallback(callbackUrl: params.CALLBACK_URL, callbackToken: params.CALLBACK_TOKEN, ")
                .append("runId: params.PIPELINE_RUN_ID, pipelineVersionId: params.PIPELINE_VERSION_ID, ")
                .append("nodeId: '").append(escapeGroovy(node.getId())).append("', ")
                .append("nodeType: '").append(escapeGroovy(node.getType())).append("', ")
                .append("nodeName: '").append(escapeGroovy(node.getName())).append("', ")
                .append("action: '").append(action).append("'");
        if (messageExpression != null) {
            builder.append(", message: ").append(messageExpression);
        }
        builder.append(")\n");
    }

    private String command(PipelineSpec.Node node) {
        Object templateKey = node.getParams().get(PARAM_COMMAND_TEMPLATE_KEY);
        PipelineCommandTemplateRespVO template = pipelineNodeRegistryService.getCommandTemplate(String.valueOf(templateKey));
        return template == null ? "" : template.getCommand();
    }

    private String param(PipelineSpec.Node node, String key, String defaultValue) {
        Object value = node.getParams() == null ? null : node.getParams().get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private String escapeGroovy(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("'", "\\'");
    }

}
