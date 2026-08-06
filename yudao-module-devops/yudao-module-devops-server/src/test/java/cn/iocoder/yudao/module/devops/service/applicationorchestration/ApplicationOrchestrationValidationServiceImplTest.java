package cn.iocoder.yudao.module.devops.service.applicationorchestration;

import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationComponentVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationConfigVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationValidationMessageRespVO;
import cn.iocoder.yudao.module.devops.service.applicationorchestration.ApplicationOrchestrationValidationService.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ApplicationOrchestrationValidationServiceImpl} 的单元测试。
 */
public class ApplicationOrchestrationValidationServiceImplTest {

    private final ApplicationOrchestrationValidationServiceImpl validationService =
            new ApplicationOrchestrationValidationServiceImpl();

    @Test
    public void testValidate_success_normalizeOrder() {
        ApplicationOrchestrationConfigVO config = config(
                component("demo-config", "demo-config", "CONFIG_MAP", List.of(),
                        "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: demo-config\ndata: {}\n"),
                component("demo-deployment", "demo-deployment", "DEPLOYMENT", List.of("demo-config"),
                        "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: demo-deployment\n"));

        ValidationResult result = validationService.validate(config);

        assertTrue(result.valid());
        assertEquals(1, result.normalizedConfig().getComponents().get(0).getApplyOrder());
        assertEquals(2, result.normalizedConfig().getComponents().get(1).getApplyOrder());
        assertEquals(2, result.resources().size());
        assertEquals("ConfigMap", result.resources().get(0).getKind());
        assertEquals("Deployment", result.resources().get(1).getKind());
    }

    @Test
    public void testValidate_allSevenTemplates() {
        ApplicationOrchestrationConfigVO config = config(
                component("s", "demo-secret", "SECRET", List.of(),
                        "apiVersion: v1\nkind: Secret\nmetadata:\n  name: demo-secret\ntype: Opaque\n"),
                component("cm", "demo-config", "CONFIG_MAP", List.of(),
                        "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: demo-config\ndata: {}\n"),
                component("pvc", "demo-pvc", "PVC", List.of(),
                        "apiVersion: v1\nkind: PersistentVolumeClaim\nmetadata:\n  name: demo-pvc\n"),
                component("svc", "demo-service", "SERVICE", List.of(),
                        "apiVersion: v1\nkind: Service\nmetadata:\n  name: demo-service\n"),
                component("dep", "demo-deployment", "DEPLOYMENT", List.of(),
                        "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: demo-deployment\n"),
                component("ing", "demo-ingress", "INGRESS", List.of(),
                        "apiVersion: networking.k8s.io/v1\nkind: Ingress\nmetadata:\n  name: demo-ingress\n"),
                component("blank", "demo-blank", "BLANK", List.of(),
                        "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: demo-blank\ndata: {}\n"));

        ValidationResult result = validationService.validate(config);

        assertTrue(result.valid());
        assertEquals(7, result.resources().size());
    }

    @Test
    public void testValidate_duplicateId() {
        ApplicationOrchestrationConfigVO config = config(
                component("dup", "name-a", "CONFIG_MAP", List.of(),
                        "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: name-a\ndata: {}\n"),
                component("dup", "name-b", "CONFIG_MAP", List.of(),
                        "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: name-b\ndata: {}\n"));

        ValidationResult result = validationService.validate(config);

        assertFalse(result.valid());
        assertTrue(hasCode(result.errors(), "COMPONENT_ID_DUPLICATE"));
    }

    @Test
    public void testValidate_duplicateName() {
        ApplicationOrchestrationConfigVO config = config(
                component("a", "same-name", "CONFIG_MAP", List.of(),
                        "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: same-name\ndata: {}\n"),
                component("b", "same-name", "CONFIG_MAP", List.of(),
                        "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: same-name\ndata: {}\n"));

        ValidationResult result = validationService.validate(config);

        assertFalse(result.valid());
        assertTrue(hasCode(result.errors(), "COMPONENT_NAME_DUPLICATE"));
    }

    @Test
    public void testValidate_dependencyNotExists() {
        ApplicationOrchestrationConfigVO config = config(
                component("dep", "demo-deployment", "DEPLOYMENT", List.of("missing"),
                        "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: demo-deployment\n"));

        ValidationResult result = validationService.validate(config);

        assertFalse(result.valid());
        assertTrue(hasCode(result.errors(), "DEPENDENCY_NOT_EXISTS"));
    }

    @Test
    public void testValidate_dependencyOrderInvalid() {
        // 依赖目标排在当前组件之后
        ApplicationOrchestrationConfigVO config = config(
                component("dep", "demo-deployment", "DEPLOYMENT", List.of("svc"),
                        "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: demo-deployment\n"),
                component("svc", "demo-service", "SERVICE", List.of(),
                        "apiVersion: v1\nkind: Service\nmetadata:\n  name: demo-service\n"));

        ValidationResult result = validationService.validate(config);

        assertFalse(result.valid());
        assertTrue(hasCode(result.errors(), "DEPENDENCY_ORDER_INVALID"));
    }

    @Test
    public void testValidate_dependencyCycle() {
        ApplicationOrchestrationComponentVO a = component("a", "comp-a", "CONFIG_MAP", List.of("b"),
                "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: comp-a\ndata: {}\n");
        ApplicationOrchestrationComponentVO b = component("b", "comp-b", "CONFIG_MAP", List.of("a"),
                "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: comp-b\ndata: {}\n");
        ValidationResult result = validationService.validate(config(a, b));

        assertFalse(result.valid());
        assertTrue(hasCode(result.errors(), "DEPENDENCY_CYCLE"));
    }

    @Test
    public void testValidate_multiDocument() {
        ApplicationOrchestrationConfigVO config = config(
                component("multi", "demo-multi", "CONFIG_MAP", List.of(),
                        "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: a\ndata: {}\n---\n"
                                + "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: b\ndata: {}\n"));

        ValidationResult result = validationService.validate(config);

        assertFalse(result.valid());
        assertTrue(hasCode(result.errors(), "YAML_MULTI_DOCUMENT"));
    }

    @Test
    public void testValidate_yamlInvalid() {
        ApplicationOrchestrationConfigVO config = config(
                component("bad", "demo-bad", "CONFIG_MAP", List.of(),
                        "apiVersion: v1\nkind: ConfigMap\n  bad-indent: : :\n"));

        ValidationResult result = validationService.validate(config);

        assertFalse(result.valid());
        assertTrue(hasCode(result.errors(), "YAML_INVALID"));
    }

    @Test
    public void testValidate_kindMismatch() {
        ApplicationOrchestrationConfigVO config = config(
                component("svc", "demo-service", "SERVICE", List.of(),
                        "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: demo-service\ndata: {}\n"));

        ValidationResult result = validationService.validate(config);

        assertFalse(result.valid());
        assertTrue(hasCode(result.errors(), "KIND_MISMATCH"));
    }

    @Test
    public void testValidate_metadataNameRequired() {
        ApplicationOrchestrationConfigVO config = config(
                component("cm", "demo-config", "CONFIG_MAP", List.of(),
                        "apiVersion: v1\nkind: ConfigMap\ndata: {}\n"));

        ValidationResult result = validationService.validate(config);

        assertFalse(result.valid());
        assertTrue(hasCode(result.errors(), "METADATA_NAME_REQUIRED"));
    }

    @Test
    public void testValidate_kindUnsupported() {
        ApplicationOrchestrationConfigVO config = config(
                component("job", "demo-job", "BLANK", List.of(),
                        "apiVersion: batch/v1\nkind: Job\nmetadata:\n  name: demo-job\n"));

        ValidationResult result = validationService.validate(config);

        assertFalse(result.valid());
        assertTrue(hasCode(result.errors(), "KIND_UNSUPPORTED"));
    }

    private static boolean hasCode(List<ApplicationOrchestrationValidationMessageRespVO> errors, String code) {
        return errors.stream().anyMatch(error -> code.equals(error.getCode()));
    }

    private static ApplicationOrchestrationConfigVO config(ApplicationOrchestrationComponentVO... components) {
        ApplicationOrchestrationConfigVO config = new ApplicationOrchestrationConfigVO();
        config.setSchemaVersion("1.0");
        config.setComponents(new ArrayList<>(List.of(components)));
        return config;
    }

    private static ApplicationOrchestrationComponentVO component(String id, String name, String templateKey,
                                                                 List<String> dependsOn, String yaml) {
        ApplicationOrchestrationComponentVO component = new ApplicationOrchestrationComponentVO();
        component.setId(id);
        component.setName(name);
        component.setTemplateKey(templateKey);
        component.setDependsOn(new ArrayList<>(dependsOn));
        component.setEnabled(Boolean.TRUE);
        component.setYaml(yaml);
        return component;
    }

}
