package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

/** POSIX shell literals used when generating build scripts. */
public final class ShellQuoting {

    private ShellQuoting() {
    }

    public static String quote(String value) {
        if (value == null || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Shell value must be non-null and contain no NUL");
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    public static String requireEnvironmentName(String name) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid environment variable name");
        }
        return name;
    }
}
