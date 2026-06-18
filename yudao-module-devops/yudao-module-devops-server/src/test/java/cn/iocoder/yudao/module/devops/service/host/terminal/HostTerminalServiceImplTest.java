package cn.iocoder.yudao.module.devops.service.host.terminal;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import cn.iocoder.yudao.module.devops.framework.host.HostSshClient;
import cn.iocoder.yudao.module.devops.service.host.EnvironmentHostService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_HOST_NOT_IN_ENVIRONMENT;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * {@link HostTerminalServiceImpl} 的单元测试。
 */
public class HostTerminalServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private HostTerminalServiceImpl hostTerminalService;

    @Mock
    private EnvironmentHostService environmentHostService;
    @Mock
    private HostSshClient hostSshClient;

    @Test
    public void testOpenTerminal_hostNotInEnvironment() {
        // 准备参数
        EnvironmentHostDO host = new EnvironmentHostDO();
        host.setId(200L);
        host.setEnvId(101L);
        when(environmentHostService.validateHostExists(eq(200L))).thenReturn(host);
        when(environmentHostService.validateHostEnvironment(eq(100L))).thenReturn(new EnvironmentDO());

        // 调用并断言
        assertServiceException(() -> hostTerminalService.openTerminal(100L, 200L),
                ENVIRONMENT_HOST_NOT_IN_ENVIRONMENT);
    }

    @Test
    public void testOpenTerminal_nonHostEnvironment() {
        // 准备参数
        EnvironmentHostDO host = new EnvironmentHostDO();
        host.setId(200L);
        host.setEnvId(100L);
        when(environmentHostService.validateHostExists(eq(200L))).thenReturn(host);
        doThrow(exception(ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED))
                .when(environmentHostService).validateHostEnvironment(eq(100L));

        // 调用并断言
        assertServiceException(() -> hostTerminalService.openTerminal(100L, 200L),
                ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED);
    }

}
