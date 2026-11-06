package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Administrator-managed shared mount mappings. No implicit local/remote path equivalence. */
@Data
@Component
@ConfigurationProperties(prefix = "yudao.devops.pipeline.ssh")
public class PipelineSshProperties {

    /** Explicit administrator allowlist keyed by environment host record id. */
    private Map<Long, WorkspaceMapping> hosts = new LinkedHashMap<>();

    @Data
    public static class WorkspaceMapping {
        /** Platform-visible shared workspace root. */
        private String localRoot;
        /** SSH host-visible root of the same shared filesystem. */
        private String remoteRoot;
    }
}
