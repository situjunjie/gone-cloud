package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineCommandTemplateRespVO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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
            if (!isJenkinsNode(node)) {
                continue;
            }
            appendStage(builder, node);
        }
        builder.append("  }\n");
        builder.append("  post {\n");
        builder.append("    always {\n");
        builder.append("      cleanWs(deleteDirs: true, disableDeferredWipeout: true, notFailBuild: true)\n");
        builder.append("    }\n");
        builder.append("  }\n");
        builder.append("}\n");
        return builder.toString();
    }

    @Override
    public String checksum(String jenkinsfileText) {
        return DigestUtil.sha256Hex(jenkinsfileText);
    }

    private void appendStage(StringBuilder builder, PipelineSpec.Node node) {
        builder.append("    stage('").append(escapeGroovy(stageName(node))).append("') {\n");
        appendStageAgent(builder, node);
        appendStageTools(builder, node);
        appendStageEnvironment(builder, node);
        appendStageOptions(builder, node);
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
            case PipelineNodeRegistryServiceImpl.TYPE_MAVEN_BUILD_JAR -> appendMavenBuildJar(builder, node);
            case PipelineNodeRegistryServiceImpl.TYPE_NPM_BUILD -> appendNpmBuild(builder, node);
            case PipelineNodeRegistryServiceImpl.TYPE_DOCKER_BUILD_PUSH -> appendDockerBuildPush(builder, node);
            case PipelineNodeRegistryServiceImpl.TYPE_ARTIFACT_UPLOAD -> appendArtifactUpload(builder, node);
            case PipelineNodeRegistryServiceImpl.TYPE_EXPORT_OFFLINE_IMAGE -> appendExportOfflineImage(builder, node);
            case PipelineNodeRegistryServiceImpl.TYPE_EXECUTE_SHELL -> appendExecuteShell(builder, node);
            case PipelineNodeRegistryServiceImpl.TYPE_SSH_PUBLISH -> appendSshPublish(builder, node);
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

    private boolean isJenkinsNode(PipelineSpec.Node node) {
        return PipelineNodeRegistryServiceImpl.isJenkinsExecutableNode(node.getType());
    }

    private void appendCheckout(StringBuilder builder) {
        builder.append("            goneDevopsCheckout(repoUrl: params.REPO_URL, branchName: params.BRANCH_NAME, commitSha: params.COMMIT_SHA)\n");
    }

    private void appendUnitTest(StringBuilder builder, PipelineSpec.Node node) {
        builder.append("            goneDevopsUnitTest(command: '").append(escapeGroovy(command(node))).append("')\n");
    }

    private void appendBuildArtifact(StringBuilder builder, PipelineSpec.Node node) {
        String templateKey = param(node, PARAM_COMMAND_TEMPLATE_KEY, "");
        if ("npm_build".equals(templateKey)) {
            builder.append("            goneDevopsNpmBuild(workingDir: '.', packageManager: 'npm', installCommand: 'npm ci', ")
                    .append("buildCommand: '").append(escapeGroovy(command(node))).append("', distPattern: '")
                    .append(escapeGroovy(param(node, "artifactPattern", "dist/**"))).append("')\n");
            return;
        }
        builder.append("            goneDevopsMavenBuildJar(workingDir: '.', goals: '")
                .append(escapeGroovy(command(node))).append("', profiles: '', skipTests: false, mavenOptions: '', ")
                .append("settingsConfigId: '', artifactPattern: '")
                .append(escapeGroovy(param(node, "artifactPattern", "**/target/*.jar"))).append("')\n");
    }

    private void appendBuildImage(StringBuilder builder, PipelineSpec.Node node) {
        builder.append("            env.IMAGE_TAG = goneDevopsDockerBuildPush(imageName: params.APP_KEY, ")
                .append("imageTag: params.COMMIT_SHA ?: env.BUILD_NUMBER, dockerfile: '")
                .append(escapeGroovy(param(node, "dockerfile", "Dockerfile"))).append("', context: '")
                .append(escapeGroovy(param(node, "context", "."))).append("', registryUrl: '', credentialsId: '', ")
                .append("push: false, pushLatest: false, buildArgs: [:])\n");
    }

    private void appendReportArtifacts(StringBuilder builder) {
        builder.append("            archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true, allowEmptyArchive: false, onlyIfSuccessful: true\n");
        builder.append("            goneDevopsReportArtifacts(pipelineRunId: params.PIPELINE_RUN_ID, imageTag: env.IMAGE_TAG)\n");
    }

    private void appendMavenBuildJar(StringBuilder builder, PipelineSpec.Node node) {
        builder.append("            goneDevopsMavenBuildJar(workingDir: '")
                .append(escapeGroovy(param(node, "workingDir", "."))).append("', goals: '")
                .append(escapeGroovy(param(node, "goals", "clean package"))).append("', profiles: '")
                .append(escapeGroovy(param(node, "profiles", ""))).append("', skipTests: ")
                .append(booleanParam(node, "skipTests", true)).append(", mavenOptions: '")
                .append(escapeGroovy(param(node, "mavenOptions", ""))).append("', settingsConfigId: '")
                .append(escapeGroovy(param(node, "settingsConfigId", ""))).append("', artifactPattern: '")
                .append(escapeGroovy(param(node, "artifactPattern", "**/target/*.jar"))).append("')\n");
    }

    private void appendNpmBuild(StringBuilder builder, PipelineSpec.Node node) {
        builder.append("            goneDevopsNpmBuild(workingDir: '")
                .append(escapeGroovy(param(node, "workingDir", "."))).append("', packageManager: '")
                .append(escapeGroovy(param(node, "packageManager", "npm"))).append("', installCommand: '")
                .append(escapeGroovy(param(node, "installCommand", "npm ci"))).append("', buildCommand: '")
                .append(escapeGroovy(param(node, "buildCommand", "npm run build"))).append("', nodeVersionTool: '")
                .append(escapeGroovy(param(node, "nodeVersionTool", ""))).append("', distPattern: '")
                .append(escapeGroovy(param(node, "distPattern", "dist/**"))).append("', cacheEnabled: ")
                .append(booleanParam(node, "cacheEnabled", false)).append(")\n");
    }

    private void appendDockerBuildPush(StringBuilder builder, PipelineSpec.Node node) {
        builder.append("            env.IMAGE_TAG = goneDevopsDockerBuildPush(imageName: '")
                .append(escapeGroovy(param(node, "imageName", "${APP_KEY}"))).append("', imageTag: '")
                .append(escapeGroovy(param(node, "imageTagExpression", "${COMMIT_SHA}"))).append("', dockerfile: '")
                .append(escapeGroovy(param(node, "dockerfile", "Dockerfile"))).append("', context: '")
                .append(escapeGroovy(param(node, "context", "."))).append("', registryUrl: '")
                .append(escapeGroovy(param(node, "registryUrl", ""))).append("', credentialsId: '")
                .append(escapeGroovy(param(node, "registryCredentialsId", ""))).append("', push: ")
                .append(booleanParam(node, "push", true)).append(", pushLatest: ")
                .append(booleanParam(node, "pushLatest", false)).append(", buildArgs: ")
                .append(groovyMapParam(node, "buildArgs")).append(")\n");
    }

    private void appendArtifactUpload(StringBuilder builder, PipelineSpec.Node node) {
        builder.append("            archiveArtifacts artifacts: '")
                .append(escapeGroovy(param(node, "artifactPattern", "**/target/*.jar"))).append("', fingerprint: ")
                .append(booleanParam(node, "fingerprint", true)).append(", allowEmptyArchive: ")
                .append(booleanParam(node, "allowEmptyArchive", false)).append(", onlyIfSuccessful: ")
                .append(booleanParam(node, "onlyIfSuccessful", true)).append("\n");
        String stashName = param(node, "stashName", "");
        if (!stashName.isBlank()) {
            builder.append("            stash name: '").append(escapeGroovy(stashName)).append("', includes: '")
                    .append(escapeGroovy(param(node, "artifactPattern", "**/target/*.jar"))).append("'\n");
        }
    }

    private void appendExportOfflineImage(StringBuilder builder, PipelineSpec.Node node) {
        builder.append("            env.OFFLINE_IMAGE_PACKAGE_METADATA = goneDevopsExportOfflineImage(imageName: '")
                .append(escapeGroovy(param(node, "imageName", "${APP_KEY}"))).append("', imageTag: '")
                .append(escapeGroovy(param(node, "imageTag", "${COMMIT_SHA}"))).append("', ossEndpoint: '")
                .append(escapeGroovy(param(node, "ossEndpoint", ""))).append("', ossBucket: '")
                .append(escapeGroovy(param(node, "ossBucket", ""))).append("', ossPath: '")
                .append(escapeGroovy(param(node, "ossPath", "offline-images/${APP_KEY}/"))).append("', ossCredentialsId: '")
                .append(escapeGroovy(param(node, "ossCredentialsId", ""))).append("')\n");
    }

    private void appendExecuteShell(StringBuilder builder, PipelineSpec.Node node) {
        String workingDir = param(node, "workingDir", ".");
        if (workingDir.isBlank() || ".".equals(workingDir)) {
            builder.append("            sh(label: '").append(escapeGroovy(stageName(node)))
                    .append("', script: '").append(escapeGroovyLiteral(param(node, "script", ""))).append("')\n");
            return;
        }
        builder.append("            dir('").append(escapeGroovy(workingDir)).append("') {\n");
        builder.append("              sh(label: '").append(escapeGroovy(stageName(node)))
                .append("', script: '").append(escapeGroovyLiteral(param(node, "script", ""))).append("')\n");
        builder.append("            }\n");
    }

    private void appendSshPublish(StringBuilder builder, PipelineSpec.Node node) {
        builder.append("            sshPublisher(publishers: [sshPublisherDesc(configName: '")
                .append(escapeGroovy(param(node, "configName", "")))
                .append("', transfers: [sshTransfer(cleanRemote: false, excludes: '', execCommand: '")
                .append(escapeGroovyLiteral(param(node, "execCommand", "")))
                .append("', execTimeout: ")
                .append(integerParam(node, "execTimeoutMillis", 120000))
                .append(", flatten: false, makeEmptyDirs: false, noDefaultExcludes: false, ")
                .append("patternSeparator: '[, ]+', remoteDirectory: '")
                .append(escapeGroovy(param(node, "remoteDirectory", "")))
                .append("', remoteDirectorySDF: false, removePrefix: '")
                .append(escapeGroovy(param(node, "removePrefix", "")))
                .append("', sourceFiles: '")
                .append(escapeGroovy(param(node, "sourceFiles", "")))
                .append("')], usePromotionTimestamp: false, useWorkspaceInPromotion: false, verbose: ")
                .append(booleanParam(node, "verbose", true))
                .append(")])\n");
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
        if ("COMPLETED".equals(action)
                && PipelineNodeRegistryServiceImpl.TYPE_EXPORT_OFFLINE_IMAGE.equals(node.getType())) {
            builder.append(", packageMetadata: env.OFFLINE_IMAGE_PACKAGE_METADATA");
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

    private boolean booleanParam(PipelineSpec.Node node, String key, boolean defaultValue) {
        Object value = node.getParams() == null ? null : node.getParams().get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private int integerParam(PipelineSpec.Node node, String key, int defaultValue) {
        Object value = node.getParams() == null ? null : node.getParams().get(key);
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private String groovyMapParam(PipelineSpec.Node node, String key) {
        Object value = node.getParams() == null ? null : node.getParams().get(key);
        if (!(value instanceof java.util.Map<?, ?> map) || map.isEmpty()) {
            return "[:]";
        }
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append("'").append(escapeGroovy(String.valueOf(entry.getKey()))).append("': '")
                    .append(escapeGroovy(String.valueOf(entry.getValue()))).append("'");
            first = false;
        }
        builder.append("]");
        return builder.toString();
    }

    private void appendStageOptions(StringBuilder builder, PipelineSpec.Node node) {
        boolean hasTimeout = node.getTimeoutSeconds() != null && node.getTimeoutSeconds() > 0;
        boolean hasRetry = node.getRetryTimes() != null && node.getRetryTimes() > 0;
        if (!hasTimeout && !hasRetry) {
            return;
        }
        builder.append("      options {\n");
        if (hasTimeout) {
            builder.append("        timeout(time: ").append(node.getTimeoutSeconds()).append(", unit: 'SECONDS')\n");
        }
        if (hasRetry) {
            builder.append("        retry(").append(node.getRetryTimes()).append(")\n");
        }
        builder.append("      }\n");
    }

    private void appendStageAgent(StringBuilder builder, PipelineSpec.Node node) {
        String agentLabel = param(node, "agentLabel", "");
        if (agentLabel.isBlank()) {
            return;
        }
        builder.append("      agent { label '").append(escapeGroovy(agentLabel)).append("' }\n");
    }

    private void appendStageTools(StringBuilder builder, PipelineSpec.Node node) {
        String toolJdk = param(node, "toolJdk", "");
        String toolMaven = param(node, "toolMaven", "");
        if (toolJdk.isBlank() && toolMaven.isBlank()) {
            return;
        }
        builder.append("      tools {\n");
        if (!toolJdk.isBlank()) {
            builder.append("        jdk '").append(escapeGroovy(toolJdk)).append("'\n");
        }
        if (!toolMaven.isBlank()) {
            builder.append("        maven '").append(escapeGroovy(toolMaven)).append("'\n");
        }
        builder.append("      }\n");
    }

    private void appendStageEnvironment(StringBuilder builder, PipelineSpec.Node node) {
        Object value = node.getParams() == null ? null : node.getParams().get("env");
        if (!(value instanceof Map<?, ?> envMap) || envMap.isEmpty()) {
            return;
        }
        builder.append("      environment {\n");
        for (Map.Entry<?, ?> entry : envMap.entrySet()) {
            builder.append("        ").append(escapeGroovy(String.valueOf(entry.getKey()))).append(" = '")
                    .append(escapeGroovy(String.valueOf(entry.getValue()))).append("'\n");
        }
        builder.append("      }\n");
    }

    private String stageName(PipelineSpec.Node node) {
        String name = param(node, "stageName", node.getName());
        if (name == null || name.isBlank()) {
            return node.getId() + "__" + node.getType();
        }
        return name;
    }

    private String escapeGroovy(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String escapeGroovyLiteral(String value) {
        return escapeGroovy(value)
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

}
