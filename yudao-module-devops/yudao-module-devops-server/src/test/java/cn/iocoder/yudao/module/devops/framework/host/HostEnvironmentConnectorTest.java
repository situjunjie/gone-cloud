package cn.iocoder.yudao.module.devops.framework.host;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentConnectionCheckRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import cn.iocoder.yudao.module.devops.dal.mysql.host.EnvironmentHostMapper;
import com.jcraft.jsch.JSchException;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HostEnvironmentConnector} 的单元测试。
 */
public class HostEnvironmentConnectorTest extends BaseMockitoUnitTest {

    @InjectMocks
    private HostEnvironmentConnector connector;

    @Mock
    private EnvironmentHostMapper environmentHostMapper;
    @Mock
    private HostSshClient hostSshClient;

    @Test
    public void testCheckConnection_summary() throws Exception {
        // 准备参数
        ReflectionTestUtils.setField(connector, "hostSshClient", hostSshClient);
        EnvironmentDO environment = new EnvironmentDO();
        environment.setId(100L);
        EnvironmentHostDO successHost = buildHost(1L);
        EnvironmentHostDO failHost = buildHost(2L);
        when(environmentHostMapper.selectListByEnvId(eq(100L))).thenReturn(List.of(successHost, failHost));
        doAnswer(invocation -> {
            EnvironmentHostDO host = invocation.getArgument(0);
            if (Long.valueOf(2L).equals(host.getId())) {
                throw new JSchException("Auth fail");
            }
            return null;
        }).when(hostSshClient).checkConnection(any(EnvironmentHostDO.class));

        // 调用
        EnvironmentConnectionCheckRespVO respVO = connector.checkConnection(environment);

        // 断言
        assertEquals("HOST", respVO.getInfraType());
        assertEquals(2, respVO.getHostCount());
        assertEquals(1, respVO.getHostSuccessCount());
        assertEquals(1, respVO.getHostFailCount());
        verify(hostSshClient).checkConnection(eq(successHost));
        verify(hostSshClient).checkConnection(eq(failHost));
    }

    private EnvironmentHostDO buildHost(Long id) {
        EnvironmentHostDO host = new EnvironmentHostDO();
        host.setId(id);
        return host;
    }

}
