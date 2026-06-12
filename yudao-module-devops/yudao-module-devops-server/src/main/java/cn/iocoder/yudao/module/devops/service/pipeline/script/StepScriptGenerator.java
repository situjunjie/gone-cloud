package cn.iocoder.yudao.module.devops.service.pipeline.script;

import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;

/**
 * 流水线构建类节点 → 纯 shell 脚本片段生成器。
 *
 * <p>取代 {@code JenkinsfileGeneratorServiceImpl} 中「节点 → Jenkins DSL」的拼装逻辑，
 * 把每个 BUILD 类节点（在构建机上执行 shell 的节点）翻译成不含任何 Jenkins/Groovy 外壳的
 * 纯 shell 脚本，交由 {@code BuildExecutor}（ST-1/ST-2）在构建机上执行。
 *
 * <h3>环境变量注入约定</h3>
 * 生成的脚本不做字符串硬插值，而是引用以下由执行引擎通过 {@code ExecContext.env} 注入的环境变量，
 * ST-5 驱动器与 ST-1 {@code ExecContext} 据此对接：
 * <ul>
 *     <li>{@code REPO_URL}      —— 代码仓库 clone 地址（凭据由 ST-3 凭据层注入到 URL，脚本不出现明文 token）</li>
 *     <li>{@code BRANCH_NAME}   —— 部署分支名</li>
 *     <li>{@code COMMIT_SHA}    —— 目标 commit（可空）</li>
 *     <li>{@code APP_KEY}       —— 应用标识，用作默认镜像名</li>
 *     <li>{@code IMAGE_TAG}     —— 解析后的镜像标签（默认取 COMMIT_SHA）</li>
 *     <li>{@code DOCKER_REGISTRY}          —— 镜像仓库地址（可选，push 时使用）</li>
 *     <li>{@code DOCKER_REGISTRY_USERNAME} —— 镜像仓库账号（凭据层注入，脚本仅引用变量名）</li>
 *     <li>{@code DOCKER_REGISTRY_PASSWORD} —— 镜像仓库口令（凭据层注入，脚本仅引用变量名）</li>
 * </ul>
 *
 * <p>凭据值一律由 env 注入，脚本里只引用变量名，永不内联明文，满足「日志/异常不得回显凭据」。
 */
public interface StepScriptGenerator {

    /**
     * 该节点类型是否由本生成器（BUILD 类）处理。
     *
     * @param nodeType 节点类型
     * @return 是否支持
     */
    boolean supports(String nodeType);

    /**
     * 生成单个节点对应的纯 shell 脚本片段。
     *
     * @param node 流水线节点（含 type + params）
     * @return 纯 shell 脚本字符串
     */
    String generate(PipelineSpec.Node node);

}
