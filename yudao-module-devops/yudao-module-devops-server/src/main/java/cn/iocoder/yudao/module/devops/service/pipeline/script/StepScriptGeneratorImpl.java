package cn.iocoder.yudao.module.devops.service.pipeline.script;

import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineCommandTemplateRespVO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryService;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * {@link StepScriptGenerator} 默认实现。
 *
 * <p>把每个 BUILD 类节点参数翻译成纯 shell 脚本片段。所有变量（仓库地址、分支、镜像标签、凭据等）
 * 由执行引擎通过环境变量注入，脚本只引用变量名，不内联凭据明文。
 */
@Service
public class StepScriptGeneratorImpl implements StepScriptGenerator {

    /** 由本生成器（BUILD 类）处理的节点类型集合。注意不含 SSH_PUBLISH —— 其去留由 ST-5 定夺。 */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            PipelineNodeRegistryServiceImpl.TYPE_CHECKOUT,
            PipelineNodeRegistryServiceImpl.TYPE_UNIT_TEST,
            PipelineNodeRegistryServiceImpl.TYPE_BUILD_ARTIFACT,
            PipelineNodeRegistryServiceImpl.TYPE_BUILD_IMAGE,
            PipelineNodeRegistryServiceImpl.TYPE_MAVEN_BUILD_JAR,
            PipelineNodeRegistryServiceImpl.TYPE_NPM_BUILD,
            PipelineNodeRegistryServiceImpl.TYPE_DOCKER_BUILD_PUSH,
            PipelineNodeRegistryServiceImpl.TYPE_ARTIFACT_UPLOAD,
            PipelineNodeRegistryServiceImpl.TYPE_REPORT_ARTIFACTS,
            PipelineNodeRegistryServiceImpl.TYPE_EXPORT_OFFLINE_IMAGE,
            PipelineNodeRegistryServiceImpl.TYPE_EXECUTE_SHELL,
            PipelineNodeRegistryServiceImpl.TYPE_MOCK);

    private static final String PARAM_COMMAND_TEMPLATE_KEY = "commandTemplateKey";

    @Resource
    private PipelineNodeRegistryService pipelineNodeRegistryService;

    @Override
    public boolean supports(String nodeType) {
        return SUPPORTED_TYPES.contains(nodeType);
    }

    @Override
    public String generate(PipelineSpec.Node node) {
        return switch (node.getType()) {
            case PipelineNodeRegistryServiceImpl.TYPE_CHECKOUT -> generateCheckout(node);
            case PipelineNodeRegistryServiceImpl.TYPE_UNIT_TEST -> generateUnitTest(node);
            case PipelineNodeRegistryServiceImpl.TYPE_BUILD_ARTIFACT -> generateBuildArtifact(node);
            case PipelineNodeRegistryServiceImpl.TYPE_BUILD_IMAGE -> generateBuildImage(node);
            case PipelineNodeRegistryServiceImpl.TYPE_MAVEN_BUILD_JAR -> generateMavenBuildJar(node);
            case PipelineNodeRegistryServiceImpl.TYPE_NPM_BUILD -> generateNpmBuild(node);
            case PipelineNodeRegistryServiceImpl.TYPE_DOCKER_BUILD_PUSH -> generateDockerBuildPush(node);
            case PipelineNodeRegistryServiceImpl.TYPE_ARTIFACT_UPLOAD -> generateArtifactUpload(node);
            case PipelineNodeRegistryServiceImpl.TYPE_REPORT_ARTIFACTS -> generateReportArtifacts(node);
            case PipelineNodeRegistryServiceImpl.TYPE_EXPORT_OFFLINE_IMAGE -> generateExportOfflineImage(node);
            case PipelineNodeRegistryServiceImpl.TYPE_EXECUTE_SHELL -> generateExecuteShell(node);
            case PipelineNodeRegistryServiceImpl.TYPE_MOCK -> generateMock(node);
            default -> throw new IllegalArgumentException("Unsupported build node type: " + node.getType());
        };
    }

    /**
     * 拉取代码：clone 部署分支并 checkout 目标 commit。
     * 仓库地址/分支/commit 通过 env 注入（REPO_URL / BRANCH_NAME / COMMIT_SHA），脚本不内联凭据。
     */
    private String generateCheckout(PipelineSpec.Node node) {
        boolean cleanBeforeCheckout = booleanParam(node, "cleanBeforeCheckout", true);
        String subdirectory = param(node, "checkoutSubdirectory", "");
        boolean shallowClone = booleanParam(node, "shallowClone", false);
        StringBuilder sb = new StringBuilder();
        sb.append("set -e\n");
        if (cleanBeforeCheckout) {
            // 复用同一工作目录的 run，clone 前清理上次检出内容
            sb.append("rm -rf ").append(subdirectory.isBlank() ? "./*" : quote(subdirectory)).append('\n');
        }
        sb.append("git clone");
        if (shallowClone) {
            sb.append(" --depth 1");
        }
        sb.append(" --branch \"$BRANCH_NAME\" \"$REPO_URL\"");
        if (!subdirectory.isBlank()) {
            sb.append(' ').append(quote(subdirectory));
        } else {
            sb.append(" .");
        }
        sb.append('\n');
        if (!subdirectory.isBlank()) {
            sb.append("cd ").append(quote(subdirectory)).append('\n');
        }
        // COMMIT_SHA 可空：非空时 checkout 到精确 commit；浅克隆需先 fetch
        sb.append("if [ -n \"$COMMIT_SHA\" ]; then\n");
        if (shallowClone) {
            sb.append("  git fetch --depth 1 origin \"$COMMIT_SHA\"\n");
        }
        sb.append("  git checkout \"$COMMIT_SHA\"\n");
        sb.append("fi\n");
        return sb.toString();
    }

    /**
     * 单元测试：执行命令模板里的测试命令（如 mvn test）。报告匹配仅作注释提示，归档由平台侧处理。
     */
    private String generateUnitTest(PipelineSpec.Node node) {
        String command = commandFromTemplate(node);
        StringBuilder sb = new StringBuilder();
        sb.append("set -e\n");
        sb.append(command).append('\n');
        return sb.toString();
    }

    /**
     * 构建制品：兼容旧节点。npm_build 模板走 npm 构建命令，其余走 maven 命令模板。
     */
    private String generateBuildArtifact(PipelineSpec.Node node) {
        String command = commandFromTemplate(node);
        StringBuilder sb = new StringBuilder();
        sb.append("set -e\n");
        sb.append(command).append('\n');
        return sb.toString();
    }

    /**
     * 构建镜像：兼容旧节点，仅本地 docker build（push=false 语义），镜像名取 APP_KEY、tag 取 IMAGE_TAG。
     */
    private String generateBuildImage(PipelineSpec.Node node) {
        String dockerfile = param(node, "dockerfile", "Dockerfile");
        String context = param(node, "context", ".");
        StringBuilder sb = new StringBuilder();
        sb.append("set -e\n");
        sb.append("docker build -f ").append(quote(dockerfile))
                .append(" -t \"$APP_KEY:$IMAGE_TAG\" ").append(quote(context)).append('\n');
        return sb.toString();
    }

    /**
     * Maven Jar 构建：拼装 mvn 命令，注入 goals / profiles / skipTests / mavenOptions。
     */
    private String generateMavenBuildJar(PipelineSpec.Node node) {
        String workingDir = param(node, "workingDir", ".");
        String goals = param(node, "goals", "clean package");
        String profiles = param(node, "profiles", "");
        boolean skipTests = booleanParam(node, "skipTests", true);
        String mavenOptions = param(node, "mavenOptions", "");
        StringBuilder sb = new StringBuilder();
        sb.append("set -e\n");
        appendCd(sb, workingDir);
        if (!mavenOptions.isBlank()) {
            sb.append("export MAVEN_OPTS=").append(quote(mavenOptions)).append('\n');
        }
        sb.append("mvn ").append(goals);
        if (!profiles.isBlank()) {
            sb.append(" -P ").append(quote(profiles));
        }
        if (skipTests) {
            sb.append(" -DskipTests");
        }
        sb.append('\n');
        return sb.toString();
    }

    /**
     * NPM 构建：依次执行 install 与 build 命令，支持 npm/pnpm/yarn。
     */
    private String generateNpmBuild(PipelineSpec.Node node) {
        String workingDir = param(node, "workingDir", ".");
        String installCommand = param(node, "installCommand", "npm ci");
        String buildCommand = param(node, "buildCommand", "npm run build");
        StringBuilder sb = new StringBuilder();
        sb.append("set -e\n");
        appendCd(sb, workingDir);
        sb.append(installCommand).append('\n');
        sb.append(buildCommand).append('\n');
        return sb.toString();
    }

    /**
     * Docker 构建推送：docker build → （可选）登录 registry → docker push。
     * imageName/imageTag 支持 ${APP_KEY}/${COMMIT_SHA} 占位，转成 shell 变量引用。
     * registry 账号/口令通过 env 注入（DOCKER_REGISTRY_USERNAME/PASSWORD），脚本不内联凭据。
     */
    private String generateDockerBuildPush(PipelineSpec.Node node) {
        String imageName = expandPlaceholders(param(node, "imageName", "${APP_KEY}"));
        String imageTag = expandPlaceholders(param(node, "imageTagExpression", "${COMMIT_SHA}"));
        String dockerfile = param(node, "dockerfile", "Dockerfile");
        String context = param(node, "context", ".");
        boolean push = booleanParam(node, "push", true);
        boolean pushLatest = booleanParam(node, "pushLatest", false);
        Map<String, Object> buildArgs = mapParam(node, "buildArgs");

        // 完整镜像引用：有 registry 时加前缀
        String imageRefExpr = imageName + ":" + imageTag;
        StringBuilder sb = new StringBuilder();
        sb.append("set -e\n");
        sb.append("IMAGE_REF=\"").append(prefixRegistry(imageRefExpr)).append("\"\n");
        sb.append("docker build -f ").append(quote(dockerfile)).append(" -t \"$IMAGE_REF\"");
        for (Map.Entry<String, Object> entry : buildArgs.entrySet()) {
            sb.append(" --build-arg ").append(quote(entry.getKey() + "=" + entry.getValue()));
        }
        sb.append(' ').append(quote(context)).append('\n');
        if (pushLatest) {
            sb.append("IMAGE_REF_LATEST=\"").append(prefixRegistry(imageName + ":latest")).append("\"\n");
            sb.append("docker tag \"$IMAGE_REF\" \"$IMAGE_REF_LATEST\"\n");
        }
        if (push) {
            // 仅当 registry 凭据 env 存在时登录，凭据值不出现在脚本里
            sb.append("if [ -n \"$DOCKER_REGISTRY_USERNAME\" ]; then\n");
            sb.append("  echo \"$DOCKER_REGISTRY_PASSWORD\" | docker login \"$DOCKER_REGISTRY\" -u \"$DOCKER_REGISTRY_USERNAME\" --password-stdin\n");
            sb.append("fi\n");
            sb.append("docker push \"$IMAGE_REF\"\n");
            if (pushLatest) {
                sb.append("docker push \"$IMAGE_REF_LATEST\"\n");
            }
        }
        return sb.toString();
    }

    /**
     * 制品归档：在构建机上能做的就是列出匹配产物（真正的归档由平台侧拉取/处理）。
     */
    private String generateArtifactUpload(PipelineSpec.Node node) {
        String artifactPattern = param(node, "artifactPattern", "**/target/*.jar");
        StringBuilder sb = new StringBuilder();
        sb.append("set -e\n");
        sb.append("ls -1 ").append(quote(artifactPattern)).append(" 2>/dev/null || true\n");
        return sb.toString();
    }

    /**
     * 上报产物：构建机侧仅列出默认产物，真正上报由平台侧 RunLog/result_json 承接。
     */
    private String generateReportArtifacts(PipelineSpec.Node node) {
        String artifactPattern = param(node, "artifactPattern", "**/target/*.jar");
        StringBuilder sb = new StringBuilder();
        sb.append("set -e\n");
        sb.append("ls -1 ").append(quote(artifactPattern)).append(" 2>/dev/null || true\n");
        return sb.toString();
    }

    /**
     * 导出离线镜像：构建机只负责 docker save 出 tar。
     * OSS 上传由平台侧（最近的 OSS plugin 统一上传）处理，不在构建机脚本里做。
     */
    private String generateExportOfflineImage(PipelineSpec.Node node) {
        String imageName = expandPlaceholders(param(node, "imageName", "${APP_KEY}"));
        String imageTag = expandPlaceholders(param(node, "imageTag", "${COMMIT_SHA}"));
        StringBuilder sb = new StringBuilder();
        sb.append("set -e\n");
        sb.append("IMAGE_REF=\"").append(imageName).append(':').append(imageTag).append("\"\n");
        sb.append("OFFLINE_IMAGE_TAR=\"${OFFLINE_IMAGE_TAR:-offline-image.tar}\"\n");
        sb.append("docker save -o \"$OFFLINE_IMAGE_TAR\" \"$IMAGE_REF\"\n");
        return sb.toString();
    }

    /**
     * 执行 Shell：用户自定义脚本，可指定工作目录。脚本体原样下发（在隔离构建机上执行）。
     */
    private String generateExecuteShell(PipelineSpec.Node node) {
        String workingDir = param(node, "workingDir", ".");
        String script = param(node, "script", "");
        StringBuilder sb = new StringBuilder();
        sb.append("set -e\n");
        appendCd(sb, workingDir);
        sb.append(script);
        if (!script.endsWith("\n")) {
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Mock 节点：仅 echo 一条消息，用于占位/联调。
     */
    private String generateMock(PipelineSpec.Node node) {
        String message = param(node, "message", node.getName());
        return "echo " + quote("MOCK node: " + (message == null ? "" : message)) + "\n";
    }

    private void appendCd(StringBuilder sb, String workingDir) {
        if (workingDir != null && !workingDir.isBlank() && !".".equals(workingDir)) {
            sb.append("cd ").append(quote(workingDir)).append('\n');
        }
    }

    /**
     * 从命令模板解析出原始 shell 命令（如 mvn test / npm run build）。
     */
    private String commandFromTemplate(PipelineSpec.Node node) {
        Object templateKey = node.getParams() == null ? null : node.getParams().get(PARAM_COMMAND_TEMPLATE_KEY);
        PipelineCommandTemplateRespVO template = pipelineNodeRegistryService.getCommandTemplate(String.valueOf(templateKey));
        return template == null || template.getCommand() == null ? "" : template.getCommand();
    }

    /**
     * 把 DSL 里的 {@code ${APP_KEY}} / {@code ${COMMIT_SHA}} 等占位符转成 shell 变量引用 {@code $APP_KEY}。
     * 这样最终值由 env 注入，而非后端硬插值。
     */
    private String expandPlaceholders(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("${", "$").replace("}", "");
    }

    /**
     * 给镜像引用拼上 registry 前缀：有 DOCKER_REGISTRY env 时加前缀，否则保持原样。
     * 返回值作为双引号字符串体（含 shell 变量引用），调用方负责外层引号。
     */
    private String prefixRegistry(String imageRefExpr) {
        return "${DOCKER_REGISTRY:+$DOCKER_REGISTRY/}" + imageRefExpr;
    }

    private Map<String, Object> mapParam(PipelineSpec.Node node, String key) {
        Object value = node.getParams() == null ? null : node.getParams().get(key);
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        }
        return Map.of();
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

    /**
     * 单引号包裹做 shell 转义，防注入：内部的单引号用 {@code '\''} 收尾再续。
     * 注意：不会展开 $ 变量 —— 仅用于固定字面量（路径、模式、镜像名片段等）。
     */
    private String quote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }

}
