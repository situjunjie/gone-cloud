package cn.iocoder.yudao.module.devops.service.applicationorchestration;

import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationTemplateRespVO;
import cn.iocoder.yudao.module.devops.enums.ApplicationOrchestrationComponentTemplateEnum;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ApplicationOrchestrationComponentTemplateServiceImpl implements ApplicationOrchestrationComponentTemplateService {
    @Override
    public List<ApplicationOrchestrationTemplateRespVO> getTemplates() {
        return List.of(
                template("SECRET", "Secret", "apiVersion: v1\nkind: Secret\nmetadata:\n  name: demo-secret\ntype: Opaque\n"),
                template("CONFIG_MAP", "ConfigMap", "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: demo-config\ndata: {}\n"),
                template("PVC", "PersistentVolumeClaim", "apiVersion: v1\nkind: PersistentVolumeClaim\nmetadata:\n  name: demo-pvc\nspec:\n  accessModes: [ReadWriteOnce]\n  resources:\n    requests:\n      storage: 1Gi\n"),
                template("SERVICE", "Service", "apiVersion: v1\nkind: Service\nmetadata:\n  name: demo-service\nspec:\n  selector:\n    app: demo\n  ports:\n    - port: 80\n      targetPort: 8080\n"),
                template("DEPLOYMENT", "Deployment", "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: demo-deployment\nspec:\n  replicas: 1\n  selector:\n    matchLabels:\n      app: demo\n  template:\n    metadata:\n      labels:\n        app: demo\n    spec:\n      containers:\n        - name: app\n          image: nginx:latest\n"),
                template("INGRESS", "Ingress", "apiVersion: networking.k8s.io/v1\nkind: Ingress\nmetadata:\n  name: demo-ingress\nspec:\n  rules: []\n"),
                template("BLANK", "Blank", "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: demo-resource\ndata: {}\n"));
    }
    private ApplicationOrchestrationTemplateRespVO template(String key, String name, String yaml) {
        String kind = ApplicationOrchestrationComponentTemplateEnum.valueOfKey(key) == null ? null : ApplicationOrchestrationComponentTemplateEnum.valueOfKey(key).getKind();
        return new ApplicationOrchestrationTemplateRespVO(key, name, kind, yaml);
    }
}
