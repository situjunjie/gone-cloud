package cn.iocoder.yudao.module.devops.framework.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuildContextPermissionsTest {

    @Test
    void normalizeMakesContextReadableByAnyUid(@TempDir Path context) throws IOException {
        Path directory = Files.createDirectory(context.resolve("assets"));
        Path secret = Files.writeString(directory.resolve("index.html"), "<html/>");
        Path script = Files.writeString(context.resolve("run.sh"), "#!/bin/sh\n");
        Files.setPosixFilePermissions(directory, Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        Files.setPosixFilePermissions(secret, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        Files.setPosixFilePermissions(script, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));

        BuildContextPermissions.normalize(context);

        // 目录必须可被任意 UID 进入，否则镜像里的非 root 用户读不到静态资源（403）。
        assertTrue(Files.getPosixFilePermissions(directory).containsAll(Set.of(
                PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_EXECUTE)), "目录应保留可进入位");
        assertTrue(Files.getPosixFilePermissions(secret).containsAll(Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_READ)), "文件应变为所有用户可读");
        // a+rX 的 X 只对目录或已有可执行位的文件生效，普通文件不能被误设为可执行。
        assertFalse(Files.getPosixFilePermissions(secret).contains(PosixFilePermission.OTHERS_EXECUTE));
        assertTrue(Files.getPosixFilePermissions(script).contains(PosixFilePermission.OTHERS_EXECUTE));
    }

    @Test
    void normalizeSkipsMissingContext(@TempDir Path context) {
        assertDoesNotThrow(() -> BuildContextPermissions.normalize(null));
        assertDoesNotThrow(() -> BuildContextPermissions.normalize(context.resolve("absent")));
    }

    @Test
    void normalizeCommandSelectsChmodFlavourAndQuotesPath() {
        assertTrue(BuildContextPermissions.normalizeCommand("./", true)
                .startsWith("/busybox/chmod -R a+rX -- './'"));
        assertTrue(BuildContextPermissions.normalizeCommand("web app", false)
                .startsWith("chmod -R a+rX -- 'web app'"));
        // 归一化失败不阻断构建，构建上下文里可能存在 FIFO 等特殊文件。
        assertTrue(BuildContextPermissions.normalizeCommand(".", true).contains("|| echo"));
    }

}
