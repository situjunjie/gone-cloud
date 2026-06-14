package cn.iocoder.yudao.module.devops.framework.build;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.nio.file.Path;
import java.util.Map;

/**
 * 构建脚本执行上下文。
 *
 * 描述一次脚本执行所需的工作目录、环境变量(用于注入凭据等)与超时时间。
 *
 * 注意:{@link #env} 可能包含凭据等敏感值,{@link #toString()} 已排除该字段,
 * 调用方记录日志时也不得回显其内容。
 */
@Getter
@Builder
@ToString
public class ExecContext {

    /**
     * 工作目录。同一 run 的多个节点脚本通常复用同一目录,使下游节点可见上游产物。
     */
    private final Path workingDir;

    /**
     * 运行 id。用于 {@link BuildExecutor#cancel(String)} 定位运行中的进程。
     */
    private final String runId;

    /**
     * 环境变量,用于注入凭据(git token / registry / ssh key 等)与构建变量
     * (REPO_URL / BRANCH_NAME / COMMIT_SHA / APP_KEY / IMAGE_TAG 等)。
     *
     * 该字段可能包含敏感值,已从 {@link #toString()} 中排除。
     */
    @ToString.Exclude
    private final Map<String, String> env;

    /**
     * 命令执行超时秒数。
     */
    private final long timeoutSeconds;

}
