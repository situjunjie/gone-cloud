package cn.iocoder.yudao.module.devops.service.host;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import cn.iocoder.yudao.module.devops.dal.mysql.host.EnvironmentHostMapper;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.enums.HostAuthTypeEnum;
import cn.iocoder.yudao.module.devops.enums.HostCheckStatusEnum;
import cn.iocoder.yudao.module.devops.framework.host.HostSshClient;
import cn.iocoder.yudao.module.devops.service.environment.EnvironmentService;
import com.jcraft.jsch.JSchException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_HOST_CONNECTION_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_HOST_PASSWORD_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EnvironmentHostServiceImpl} 的单元测试。
 */
public class EnvironmentHostServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EnvironmentHostServiceImpl environmentHostService;

    @Mock
    private EnvironmentHostMapper environmentHostMapper;
    @Mock
    private EnvironmentService environmentService;
    @Mock
    private HostSshClient hostSshClient;

    @Test
    public void testCreateHost_success() {
        // 准备参数
        EnvironmentHostSaveReqVO reqVO = buildPasswordReqVO();
        when(environmentService.validateEnvironmentExists(eq(100L))).thenReturn(buildHostEnvironment());
        doAnswer(invocation -> {
            EnvironmentHostDO host = invocation.getArgument(0);
            host.setId(200L);
            return 1;
        }).when(environmentHostMapper).insert(any(EnvironmentHostDO.class));

        // 调用
        Long id = environmentHostService.createHost(reqVO);

        // 断言
        assertEquals(200L, id);
        ArgumentCaptor<EnvironmentHostDO> captor = ArgumentCaptor.forClass(EnvironmentHostDO.class);
        verify(environmentHostMapper).insert(captor.capture());
        EnvironmentHostDO host = captor.getValue();
        assertEquals(100L, host.getEnvId());
        assertEquals("app-01", host.getHostKey());
        assertEquals("secret", host.getPassword());
        assertNull(host.getPrivateKey());
    }

    @Test
    public void testCreateHost_nonHostEnvironment() {
        // 准备参数
        EnvironmentHostSaveReqVO reqVO = buildPasswordReqVO();
        EnvironmentDO environment = buildHostEnvironment();
        environment.setInfraType(EnvironmentInfraTypeEnum.DOCKER.getInfraType());
        when(environmentService.validateEnvironmentExists(eq(100L))).thenReturn(environment);

        // 调用并断言
        assertServiceException(() -> environmentHostService.createHost(reqVO), ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED);
    }

    @Test
    public void testCreateHost_missingPassword() {
        // 准备参数
        EnvironmentHostSaveReqVO reqVO = buildPasswordReqVO();
        reqVO.setPassword(null);
        when(environmentService.validateEnvironmentExists(eq(100L))).thenReturn(buildHostEnvironment());

        // 调用并断言
        assertServiceException(() -> environmentHostService.createHost(reqVO), ENVIRONMENT_HOST_PASSWORD_REQUIRED);
    }

    @Test
    public void testUpdateHost_keepOldPassword() {
        // 准备参数
        EnvironmentHostSaveReqVO reqVO = buildPasswordReqVO();
        reqVO.setId(200L);
        reqVO.setPassword(null);
        EnvironmentHostDO oldHost = buildHost();
        oldHost.setPassword("old-secret");
        when(environmentHostMapper.selectById(eq(200L))).thenReturn(oldHost);
        when(environmentService.validateEnvironmentExists(eq(100L))).thenReturn(buildHostEnvironment());

        // 调用
        environmentHostService.updateHost(reqVO);

        // 断言
        ArgumentCaptor<EnvironmentHostDO> captor = ArgumentCaptor.forClass(EnvironmentHostDO.class);
        verify(environmentHostMapper).updateById(captor.capture());
        assertEquals("old-secret", captor.getValue().getPassword());
    }

    @Test
    public void testCheckHost_success() throws Exception {
        // 准备参数
        EnvironmentHostDO host = buildHost();
        when(environmentHostMapper.selectById(eq(200L))).thenReturn(host);
        when(environmentService.validateEnvironmentExists(eq(100L))).thenReturn(buildHostEnvironment());

        // 调用
        environmentHostService.checkHost(200L);

        // 断言
        ArgumentCaptor<EnvironmentHostDO> captor = ArgumentCaptor.forClass(EnvironmentHostDO.class);
        verify(environmentHostMapper).updateById(captor.capture());
        assertEquals(HostCheckStatusEnum.SUCCESS.getStatus(), captor.getValue().getLastCheckStatus());
        assertEquals("连接成功", captor.getValue().getLastCheckMessage());
    }

    @Test
    public void testCheckHost_fail() throws Exception {
        // 准备参数
        EnvironmentHostDO host = buildHost();
        when(environmentHostMapper.selectById(eq(200L))).thenReturn(host);
        when(environmentService.validateEnvironmentExists(eq(100L))).thenReturn(buildHostEnvironment());
        doThrow(new JSchException("Auth fail")).when(hostSshClient).checkConnection(eq(host));

        // 调用并断言
        assertServiceException(() -> environmentHostService.checkHost(200L), ENVIRONMENT_HOST_CONNECTION_FAIL, "Auth fail");
        ArgumentCaptor<EnvironmentHostDO> captor = ArgumentCaptor.forClass(EnvironmentHostDO.class);
        verify(environmentHostMapper).updateById(captor.capture());
        assertEquals(HostCheckStatusEnum.FAIL.getStatus(), captor.getValue().getLastCheckStatus());
        assertEquals("Auth fail", captor.getValue().getLastCheckMessage());
    }

    private EnvironmentHostSaveReqVO buildPasswordReqVO() {
        EnvironmentHostSaveReqVO reqVO = new EnvironmentHostSaveReqVO();
        reqVO.setEnvId(100L);
        reqVO.setHostKey("app-01");
        reqVO.setHostName("应用服务器 01");
        reqVO.setHost("192.168.1.10");
        reqVO.setPort(22);
        reqVO.setUsername("root");
        reqVO.setAuthType(HostAuthTypeEnum.PASSWORD.getAuthType());
        reqVO.setPassword("secret");
        reqVO.setStatus(0);
        return reqVO;
    }

    private EnvironmentHostDO buildHost() {
        EnvironmentHostDO host = new EnvironmentHostDO();
        host.setId(200L);
        host.setEnvId(100L);
        host.setHostKey("app-01");
        host.setHostName("应用服务器 01");
        host.setHost("192.168.1.10");
        host.setPort(22);
        host.setUsername("root");
        host.setAuthType(HostAuthTypeEnum.PASSWORD.getAuthType());
        host.setPassword("secret");
        return host;
    }

    private EnvironmentDO buildHostEnvironment() {
        EnvironmentDO environment = new EnvironmentDO();
        environment.setId(100L);
        environment.setInfraType(EnvironmentInfraTypeEnum.HOST.getInfraType());
        return environment;
    }

}
