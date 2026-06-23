package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DockerPipelineCommandExecutor} 的单元测试。
 */
public class DockerPipelineCommandExecutorTest extends BaseMockitoUnitTest {

    private final DockerPipelineCommandExecutor executor = new DockerPipelineCommandExecutor();

    @Test
    public void testBuildScript_withLogRedirect() {
        PipelineCommandContext context = PipelineCommandContext.builder()
                .stdoutLogPath("/workspace/.gone-devops/logs/run-log-1/stdout.log")
                .stderrLogPath("/workspace/.gone-devops/logs/run-log-1/stderr.log")
                .build();

        String script = executor.buildScript(context, "echo hello");

        assertTrue(script.contains("mkdir -p \"$(dirname '/workspace/.gone-devops/logs/run-log-1/stdout.log')\""));
        assertTrue(script.contains("echo hello"));
        assertTrue(script.contains("> '/workspace/.gone-devops/logs/run-log-1/stdout.log'"));
        assertTrue(script.contains("2> '/workspace/.gone-devops/logs/run-log-1/stderr.log'"));
    }

    @Test
    public void testBuildScript_withoutLogRedirect() {
        PipelineCommandContext context = PipelineCommandContext.builder().build();

        String script = executor.buildScript(context, "echo hello");

        assertEquals("echo hello", script);
    }

}
