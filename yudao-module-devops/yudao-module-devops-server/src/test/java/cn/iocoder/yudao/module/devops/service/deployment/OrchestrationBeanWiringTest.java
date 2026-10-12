package cn.iocoder.yudao.module.devops.service.deployment;

import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.service.application.ApplicationService;
import cn.iocoder.yudao.module.devops.service.application.ApplicationServiceImpl;
import cn.iocoder.yudao.module.devops.service.applicationorchestration.ApplicationOrchestrationService;
import cn.iocoder.yudao.module.devops.service.applicationorchestration.ApplicationOrchestrationServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionAsyncService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionEngine;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.K8sDeployStepHandler;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepHandlerRegistry;
import jakarta.annotation.Resource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 真实应用/执行/部署/编排 Bean 链，外部资源 mock；保留异步与缓存代理。 */
class OrchestrationBeanWiringTest {

    private static final List<Class<?>> SERVICES = List.of(ApplicationServiceImpl.class,
            PipelineExecutionAsyncService.class, PipelineExecutionEngine.class, PipelineStepHandlerRegistry.class,
            K8sDeployStepHandler.class, DeploymentOrderServiceImpl.class, ApplicationOrchestrationServiceImpl.class);

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testContextStartsWithoutCircularReferences(boolean applicationFirst) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getDefaultListableBeanFactory().setAllowCircularReferences(false);
            context.register(ProxyConfig.class);
            // 两种初始化顺序，避免仅因 Bean 创建顺序而侥幸通过。
            if (applicationFirst) {
                context.register(ApplicationServiceImpl.class);
            } else {
                context.register(DeploymentOrderServiceImpl.class);
            }
            SERVICES.forEach(context::register);
            registerExternalDependencies(context);
            context.refresh();

            ApplicationService application = context.getBean(ApplicationService.class);
            ApplicationOrchestrationService orchestration = context.getBean(ApplicationOrchestrationService.class);
            DeploymentOrderService deployment = context.getBean(DeploymentOrderService.class);
            assertTrue(AopUtils.isAopProxy(application));
            assertTrue(AopUtils.isAopProxy(context.getBean(PipelineExecutionAsyncService.class)));
            assertSame(orchestration, ReflectionTestUtils.getField(AopTestUtils.<Object>getUltimateTargetObject(deployment),
                    "applicationOrchestrationService"));
            assertSame(context.getBean(K8sDeployStepHandler.class),
                    context.getBean(PipelineStepHandlerRegistry.class).resolve("K8sDeploy"));

            ApplicationMapper mapper = context.getBean(ApplicationMapper.class);
            ApplicationDO record = new ApplicationDO();
            record.setId(10L);
            record.setAppKey("demo");
            when(mapper.selectById(10L)).thenReturn(record);
            assertEquals("demo", orchestration.getOrchestration(10L, "K8S_DEPLOYMENT").getAppKey());
            verify(mapper).selectById(10L);
        }
    }

    private void registerExternalDependencies(AnnotationConfigApplicationContext context) {
        for (Class<?> service : SERVICES) {
            for (Field field : service.getDeclaredFields()) {
                Resource resource = field.getAnnotation(Resource.class);
                if (resource == null || SERVICES.stream().anyMatch(field.getType()::isAssignableFrom)) {
                    continue;
                }
                String name = resource.name().isEmpty() ? field.getName() : resource.name();
                if (!context.getBeanFactory().containsSingleton(name)) {
                    context.getBeanFactory().registerSingleton(name, mock(field.getType()));
                }
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching(proxyTargetClass = true)
    @EnableAsync(proxyTargetClass = true)
    static class ProxyConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }
}
