package cn.iocoder.yudao.module.devops.service.pipeline.jenkins;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.framework.jenkins.JenkinsConsoleChunk;
import cn.iocoder.yudao.module.devops.framework.jenkins.JenkinsPipelineClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_JENKINS_CONSOLE_UNAVAILABLE;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_RUN_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PipelineJenkinsConsoleServiceImplTest extends BaseMockitoUnitTest {

    private PipelineJenkinsConsoleServiceImpl consoleService;

    @Mock
    private PipelineRunMapper pipelineRunMapper;
    @Mock
    private JenkinsPipelineClient jenkinsPipelineClient;

    @BeforeEach
    public void setUp() {
        consoleService = new PipelineJenkinsConsoleServiceImpl();
        ReflectionTestUtils.setField(consoleService, "pipelineRunMapper", pipelineRunMapper);
        ReflectionTestUtils.setField(consoleService, "jenkinsPipelineClient", jenkinsPipelineClient);
        ReflectionTestUtils.setField(consoleService, "applicationTaskExecutor", new SyncTaskExecutor());
    }

    @Test
    public void testStreamConsole_success() {
        // 准备参数
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setJenkinsBuildNumber("58");
        when(pipelineRunMapper.selectById(800L)).thenReturn(run);
        when(jenkinsPipelineClient.isEnabled()).thenReturn(true);
        when(jenkinsPipelineClient.getConsoleText("58", 12L))
                .thenReturn(new JenkinsConsoleChunk("line 1\n", 42L, false));

        // 调用
        SseEmitter emitter = consoleService.streamConsole(800L, 12L);

        // 断言
        assertNotNull(emitter);
        verify(jenkinsPipelineClient).getConsoleText("58", 12L);
    }

    @Test
    public void testStreamConsole_runNotExists() {
        // 准备参数
        when(pipelineRunMapper.selectById(800L)).thenReturn(null);

        // 调用 & 断言
        assertServiceException(() -> consoleService.streamConsole(800L, 0L), PIPELINE_RUN_NOT_EXISTS);
    }

    @Test
    public void testStreamConsole_jenkinsDisabled() {
        // 准备参数
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setJenkinsBuildNumber("58");
        when(pipelineRunMapper.selectById(800L)).thenReturn(run);
        when(jenkinsPipelineClient.isEnabled()).thenReturn(false);

        // 调用 & 断言
        assertServiceException(() -> consoleService.streamConsole(800L, 0L),
                PIPELINE_JENKINS_CONSOLE_UNAVAILABLE, "Jenkins 未启用");
    }

    @Test
    public void testStreamConsole_buildNumberNotReady() {
        // 准备参数
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        when(pipelineRunMapper.selectById(800L)).thenReturn(run);
        when(jenkinsPipelineClient.isEnabled()).thenReturn(true);

        // 调用 & 断言
        assertServiceException(() -> consoleService.streamConsole(800L, 0L),
                PIPELINE_JENKINS_CONSOLE_UNAVAILABLE, "Jenkins 构建编号尚未生成");
    }

}
