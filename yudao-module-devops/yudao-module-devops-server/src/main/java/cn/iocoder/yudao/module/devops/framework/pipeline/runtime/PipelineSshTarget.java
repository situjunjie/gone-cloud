package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import java.util.Map;
import java.util.Set;

/** Command SSH parameters: only a host record reference, never inline credentials. */
public final class PipelineSshTarget {

    public static final String HOST_ID = "sshHostId";
    private static final Set<String> COMMAND_KEYS = Set.of("run", "env", HOST_ID);

    private PipelineSshTarget() {
    }

    public static boolean isSsh(Map<String, Object> with) {
        return with != null && with.containsKey(HOST_ID);
    }

    public static long hostId(Object value) {
        String text = String.valueOf(value);
        if (!text.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("sshHostId must be a positive host id");
        }
        return Long.parseLong(text);
    }

    public static boolean hasUnsupportedKeys(Map<String, Object> with) {
        return with.keySet().stream().anyMatch(key -> !COMMAND_KEYS.contains(key));
    }
}
