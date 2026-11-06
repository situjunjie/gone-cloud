package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineSshProperties;
import cn.iocoder.yudao.module.devops.service.host.EnvironmentHostService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PipelineSshTargetResolverTest {

    private final PipelineSshTargetResolver resolver = new PipelineSshTargetResolver();
    private final EnvironmentHostService hostService = mock(EnvironmentHostService.class);
    private final ApplicationEnvMapper appEnvMapper = mock(ApplicationEnvMapper.class);
    private final PipelineSshProperties properties = new PipelineSshProperties();
    private final PipelineRunDO run = new PipelineRunDO();
    private final ApplicationEnvDO appEnv = new ApplicationEnvDO();
    private final EnvironmentHostDO host = new EnvironmentHostDO();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(resolver, "environmentHostService", hostService);
        ReflectionTestUtils.setField(resolver, "applicationEnvMapper", appEnvMapper);
        ReflectionTestUtils.setField(resolver, "properties", properties);
        TenantContextHolder.setTenantId(1L);
        run.setTenantId(1L);
        run.setAppId(10L);
        run.setApplicationEnvId(20L);
        appEnv.setTenantId(1L);
        appEnv.setAppId(10L);
        appEnv.setEnvId(30L);
        host.setTenantId(1L);
        host.setEnvId(40L);
        when(appEnvMapper.selectById(20L)).thenReturn(appEnv);
        when(hostService.validateHostExists(42L)).thenReturn(host);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void resolvesHostInOwnHostEnvironmentIndependentOfDeploymentEnvironment() {
        assertSame(host, resolver.resolveHost(run, 42));
        verify(hostService).validateHostEnvironment(40L);
    }

    @Test
    void k8sAndDockerDeploymentCanUseSeparateHostBuildEnvironment() {
        var environmentService = mock(cn.iocoder.yudao.module.devops.service.environment.EnvironmentService.class);
        var actualHostService = spy(new cn.iocoder.yudao.module.devops.service.host.EnvironmentHostServiceImpl());
        ReflectionTestUtils.setField(actualHostService, "environmentService", environmentService);
        doReturn(host).when(actualHostService).validateHostExists(42L);
        ReflectionTestUtils.setField(resolver, "environmentHostService", actualHostService);
        var deployment = new cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO();
        var builder = new cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO();
        builder.setInfraType("HOST");
        when(environmentService.validateEnvironmentExists(30L)).thenReturn(deployment);
        when(environmentService.validateEnvironmentExists(40L)).thenReturn(builder);

        for (String infra : new String[]{"K8S", "DOCKER"}) {
            deployment.setInfraType(infra);
            assertSame(host, resolver.resolveHost(run, 42));
        }
        verify(environmentService, never()).validateEnvironmentExists(30L);
        verify(environmentService, times(2)).validateEnvironmentExists(40L);
    }

    @Test
    void rejectsMissingIgnoredOrDifferentTenantBeforeLookup() {
        TenantContextHolder.clear();
        assertThrows(ServiceException.class, () -> resolver.resolveHost(run, 42));
        TenantContextHolder.setTenantId(2L);
        assertThrows(ServiceException.class, () -> resolver.resolveHost(run, 42));
        TenantContextHolder.setTenantId(1L);
        TenantContextHolder.setIgnore(true);
        assertThrows(ServiceException.class, () -> resolver.resolveHost(run, 42));
        verifyNoInteractions(hostService, appEnvMapper);
    }

    @Test
    void rejectsHostFromOtherTenantAndWrongAppRelation() {
        host.setTenantId(2L);
        assertThrows(ServiceException.class, () -> resolver.resolveHost(run, 42));
        host.setTenantId(1L);
        appEnv.setAppId(11L);
        assertThrows(ServiceException.class, () -> resolver.resolveHost(run, 42));
        appEnv.setAppId(10L);
        appEnv.setTenantId(2L);
        assertThrows(ServiceException.class, () -> resolver.resolveHost(run, 42));
        verify(hostService, never()).validateHostEnvironment(any());
    }

    @Test
    void requiresApplicationEnvironmentAndHostInfrastructure() {
        run.setApplicationEnvId(null);
        assertThrows(ServiceException.class, () -> resolver.resolveHost(run, 42));
        run.setApplicationEnvId(20L);
        doThrow(new IllegalArgumentException("not HOST")).when(hostService).validateHostEnvironment(40L);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveHost(run, 42));
    }

    @Test
    void mapsOnlyExplicitSharedRootAndRejectsUnsafePaths() {
        assertThrows(ServiceException.class, () -> resolver.resolveWorkspace(42, Path.of("/data/work/app")));
        PipelineSshProperties.WorkspaceMapping mapping = new PipelineSshProperties.WorkspaceMapping();
        mapping.setLocalRoot("/data/work");
        mapping.setRemoteRoot("/srv/pipeline");
        properties.getHosts().put(42L, mapping);
        assertEquals("/srv/pipeline/tenant-1/app-10", resolver.resolveWorkspace(42, Path.of("/data/work/tenant-1/app-10")));
        assertThrows(ServiceException.class, () -> resolver.resolveWorkspace(42, Path.of("/data/work-other/app")));
        assertThrows(ServiceException.class, () -> resolver.resolveWorkspace(42, Path.of("/data/work/../escape")));
        mapping.setRemoteRoot("/");
        assertThrows(ServiceException.class, () -> resolver.resolveWorkspace(42, Path.of("/data/work/app")));
        mapping.setRemoteRoot("relative");
        assertThrows(ServiceException.class, () -> resolver.resolveWorkspace(42, Path.of("/data/work/app")));
        mapping.setRemoteRoot("/srv/pipeline");
        mapping.setLocalRoot("/");
        assertThrows(ServiceException.class, () -> resolver.resolveWorkspace(42, Path.of("/data/work/app")));
    }
}
