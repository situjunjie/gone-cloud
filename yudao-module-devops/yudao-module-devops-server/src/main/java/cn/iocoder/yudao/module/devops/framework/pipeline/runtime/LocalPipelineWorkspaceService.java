package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 本机流水线任务工作目录服务。
 */
@Component
public class LocalPipelineWorkspaceService implements PipelineWorkspaceService {

    @Value("${yudao.devops.pipeline.workspace-root:${java.io.tmpdir}/gone-devops/pipeline-workspaces}")
    private String workspaceRoot;

    @Override
    public Path createWorkspace(PipelineRunDO run, PipelineSpec.ExecutableJob job) {
        Path workspace = Path.of(workspaceRoot, "run-" + run.getId(), "job-" + job.getJobId() + "-" + shortId());
        try {
            Files.createDirectories(workspace);
            return workspace;
        } catch (IOException ex) {
            throw new IllegalStateException("Create workspace failed: " + ex.getMessage(), ex);
        }
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

}
