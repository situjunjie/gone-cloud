package cn.iocoder.yudao.module.devops.framework.build;

import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.ShellQuoting;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/**
 * 构建上下文权限归一化工具。
 *
 * <p>Docker 构建会原样保留构建上下文里源文件的权限位：Kaniko 的 {@code COPY}、docker-java 的
 * {@code build} 都是如此。一旦上下文里出现 {@code 0600} 文件或 {@code 0700} 目录，构建出的镜像里
 * 同样是 {@code 0600}/{@code 0700}。镜像以非 root 用户运行时（例如 nginx 镜像的 {@code uid=101}）
 * 就读不到这些文件，静态资源会返回 403。
 *
 * <p>平台侧的历史成因是 K8s exec 包装脚本用 {@code umask 077} 覆盖了整条命令，使 CodeMerge 等步骤
 * 产出的工作区文件变成 {@code 0600}。除了不再泄漏 umask，构建前仍需要做一次归一化：应用环境工作区是
 * 跨 run 复用的稳定目录，历史 run 留下的 {@code 0600} 文件不会因为重新 checkout 而被修复。
 *
 * <p>归一化等价于 {@code chmod -R a+rX}：所有用户可读，目录以及本来就带可执行位的文件额外可进入/可执行，
 * 普通文件不会被误设为可执行。构建上下文是平台托管的临时工作区，放宽权限不改变宿主的安全边界。
 */
@Slf4j
public final class BuildContextPermissions {

    /**
     * 所有用户可读的权限位。
     */
    private static final Set<PosixFilePermission> READ_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ);

    /**
     * 所有用户可进入/可执行的权限位，仅用于目录或已经带可执行位的文件。
     */
    private static final Set<PosixFilePermission> EXECUTE_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_EXECUTE);

    private BuildContextPermissions() {
    }

    /**
     * 把构建上下文归一化为“任何 UID 都可读”。
     *
     * <p>用于构建发生在平台宿主机的场景（docker-java）。不跟随符号链接，单个条目处理失败时跳过，
     * 避免因为工作区里的特殊文件导致整次构建失败。
     *
     * @param context 构建上下文目录；为空或不是目录时直接返回
     */
    public static void normalize(Path context) {
        if (context == null || !Files.isDirectory(context)) {
            return;
        }
        try {
            Files.walkFileTree(context, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    applyPermissions(dir, true);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // 符号链接不在 walkFileTree 的 visitFile 里出现（默认不跟随），无需额外判断。
                    applyPermissions(file, false);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // 单个条目不可访问时继续遍历，权限归一化是尽力而为。
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            log.warn("[normalize][构建上下文权限归一化失败, context({}), type({})]",
                    context, ex.getClass().getSimpleName());
        }
    }

    /**
     * 生成在构建容器内执行权限归一化的 shell 片段。
     *
     * <p>用于构建发生在 Pod 内的场景（Kaniko），此时工作区是共享 PVC，平台侧无法直接改权限。
     *
     * @param context 容器内构建上下文路径，相对工作目录或绝对路径均可
     * @param busybox 是否使用 busybox 多合一命令；Kaniko debug 镜像只有 {@code /busybox/*}
     * @return 可直接拼进构建脚本的 shell 行，末尾带换行
     */
    public static String normalizeCommand(String context, boolean busybox) {
        String chmod = busybox ? "/busybox/chmod" : "chmod";
        // 归一化失败不阻断构建：构建上下文里可能存在 FIFO 等特殊文件，chmod 会报错但不影响 COPY。
        return chmod + " -R a+rX -- " + ShellQuoting.quote(context)
                + " || echo 'WARN: build context permission normalization was incomplete'\n";
    }

    /**
     * 计算并写入目标权限。
     *
     * @param path      目标路径
     * @param directory 是否为目录；目录始终保留可进入位
     */
    private static void applyPermissions(Path path, boolean directory) {
        try {
            Set<PosixFilePermission> current = Files.getPosixFilePermissions(path);
            Set<PosixFilePermission> target = EnumSet.copyOf(current);
            target.addAll(READ_PERMISSIONS);
            if (directory || current.contains(PosixFilePermission.OWNER_EXECUTE)) {
                target.addAll(EXECUTE_PERMISSIONS);
            }
            if (!target.equals(current)) {
                Files.setPosixFilePermissions(path, target);
            }
        } catch (UnsupportedOperationException | IOException ex) {
            // 非 POSIX 文件系统（如 Windows 开发机）或权限不足时跳过。
        }
    }

}
