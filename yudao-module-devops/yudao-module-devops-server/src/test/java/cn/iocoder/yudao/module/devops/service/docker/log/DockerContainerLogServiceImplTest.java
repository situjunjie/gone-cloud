package cn.iocoder.yudao.module.devops.service.docker.log;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DockerContainerLogServiceImpl} 的单元测试。
 */
public class DockerContainerLogServiceImplTest extends BaseMockitoUnitTest {

    private final DockerContainerLogServiceImpl dockerContainerLogService = new DockerContainerLogServiceImpl();

    @Test
    public void testIsClientDisconnected_asyncRequestNotUsable() {
        assertTrue(dockerContainerLogService.isClientDisconnected(
                new AsyncRequestNotUsableException("ServletOutputStream failed to flush")));
    }

    @Test
    public void testIsClientDisconnected_connectionResetCause() {
        RuntimeException ex = new RuntimeException("send failed",
                new IOException("Connection reset by peer"));

        assertTrue(dockerContainerLogService.isClientDisconnected(ex));
    }

    @Test
    public void testIsClientDisconnected_regularException() {
        assertFalse(dockerContainerLogService.isClientDisconnected(new IllegalStateException("docker failed")));
        assertFalse(dockerContainerLogService.isClientDisconnected(new IOException("docker stream failed")));
    }

}
