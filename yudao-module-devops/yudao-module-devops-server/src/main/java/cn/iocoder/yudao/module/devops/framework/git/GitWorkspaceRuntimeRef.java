package cn.iocoder.yudao.module.devops.framework.git;

import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import lombok.Data;

/** Persistable Pod identity; connection credentials are resolved only at execution time. */
@Data
public class GitWorkspaceRuntimeRef {
    /** Build cluster environment identifier. */
    private Long environmentId;
    /** Build namespace. */
    private String namespace;
    /** Pod name. */
    private String podName;
    /** Repository provider used to resolve short-lived Git credentials. */
    private Long repositoryProviderId;

    public static GitWorkspaceRuntimeRef from(PipelineJobRuntime runtime) {
        GitWorkspaceRuntimeRef ref = new GitWorkspaceRuntimeRef();
        ref.setEnvironmentId(((Number) runtime.getMetadata().get("environmentId")).longValue());
        ref.setNamespace((String) runtime.getMetadata().get("namespace"));
        ref.setPodName(runtime.getRuntimeId());
        return ref;
    }
}
