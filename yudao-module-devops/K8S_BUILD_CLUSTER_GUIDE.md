# K8s 构建集群配置示例

> 2026-09-07：本文下方流水线示例属于旧草案，`type`/步骤列表格式不可直接用于当前解析器。请使用[当前 DSL 示例与验收记录](../.trellis/tasks/08-31-devops-global-config-build-cluster/notes.md)。Kaniko 由独立 debug 容器执行；不要将无 shell 的 Kaniko 镜像配置为 builder。

## 应用配置文件 (application-prod.yml)

```yaml
# DevOps 构建配置
devops:
  config:
    build:
      # 默认执行器类型: DOCKER, KUBERNETES
      default-executor: KUBERNETES
      
      cluster:
        # K8s 环境 ID（需要先在系统中创建 K8s 环境）
        environment-id: 1
        
        # 构建任务命名空间
        namespace: devops-build
        
        # 默认构建镜像
        default-image: maven:3.8-openjdk-17
        
        # Kaniko 执行器镜像
        kaniko-image: gcr.io/kaniko-project/executor:v1.23.2-debug
        
        # 工作空间存储配置
        workspace-storage:
          # 存储类型: SHARED_PVC, DYNAMIC_PVC, EMPTY_DIR
          type: SHARED_PVC
          # 共享 PVC 名称
          shared-pvc-name: pipeline-shared-workspace
          # 动态 PVC 存储类
          storage-class-name: fast-ssd
          # 存储容量
          storage-size: 10Gi
        
        # 资源配额
        resource-quota:
          cpu-request: 1000m
          memory-request: 2Gi
          cpu-limit: 2000m
          memory-limit: 4Gi
        
        # Pod 清理配置
        pod-cleanup:
          enabled: true
          # 保留时长（小时）
          retention-hours: 24
```

## K8s 集群准备

### 1. 创建命名空间

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: devops-build
  labels:
    name: devops-build
```

### 2. 创建 ServiceAccount 和 RBAC

```yaml
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: pipeline-builder
  namespace: devops-build

---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: pipeline-builder-role
  namespace: devops-build
rules:
  - apiGroups: [""]
    resources: ["pods", "pods/log", "pods/exec"]
    verbs: ["create", "get", "list", "delete", "watch", "patch", "update"]
  - apiGroups: [""]
    resources: ["persistentvolumeclaims"]
    verbs: ["create", "get", "list", "delete", "patch", "update"]
  - apiGroups: [""]
    resources: ["configmaps", "secrets"]
    verbs: ["get", "list"]

---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: pipeline-builder-binding
  namespace: devops-build
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: pipeline-builder-role
subjects:
  - kind: ServiceAccount
    name: pipeline-builder
    namespace: devops-build
```

### 3. 创建共享 PVC（推荐）

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: pipeline-shared-workspace
  namespace: devops-build
spec:
  accessModes:
    - ReadWriteMany  # 需要 NFS 等支持 RWX 的存储
  storageClassName: nfs-client  # 根据实际情况修改
  resources:
    requests:
      storage: 100Gi
```

### 4. 配置镜像拉取凭证（如需要）

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: registry-credentials
  namespace: devops-build
type: kubernetes.io/dockerconfigjson
data:
  .dockerconfigjson: <base64-encoded-docker-config>
```

## Pipeline YAML 配置示例

### 示例 1: 使用 K8s 执行命令

```yaml
jobs:
  build:
    runs-on:
      group: kubernetes/build-cluster
      container: maven:3.8-openjdk-17
    steps:
      - name: 构建项目
        type: COMMAND
        with:
          run: |
            mvn clean package -DskipTests
            ls -lh target/
```

### 示例 2: 使用 Kaniko 构建镜像

```yaml
jobs:
  build-image:
    runs-on:
      group: kubernetes/build-cluster
      container: gcr.io/kaniko-project/executor:v1.9.0
    steps:
      - name: 构建 Docker 镜像
        type: KANIKO_DOCKER_BUILD
        with:
          dockerfile: Dockerfile
          context: .
          image: registry.example.com/myapp:${COMMIT_SHA}
          cache: true
          cacheRepo: registry.example.com/myapp-cache
          buildArgs:
            APP_VERSION: ${COMMIT_SHA}
            BUILD_DATE: ${BUILD_DATE}
```

### 示例 3: 完整的构建发布流程

```yaml
jobs:
  checkout:
    runs-on:
      group: kubernetes/build-cluster
      container: alpine/git:latest
    steps:
      - name: 检出代码
        type: CODE_CHECKOUT
        
  build:
    runs-on:
      group: kubernetes/build-cluster
      container: maven:3.8-openjdk-17
    depends-on: [checkout]
    steps:
      - name: Maven 构建
        type: COMMAND
        with:
          run: |
            mvn clean package -DskipTests
            
  docker-build:
    runs-on:
      group: kubernetes/build-cluster
      container: gcr.io/kaniko-project/executor:v1.9.0
    depends-on: [build]
    steps:
      - name: 构建镜像
        type: KANIKO_DOCKER_BUILD
        with:
          dockerfile: Dockerfile
          image: registry.example.com/myapp:${COMMIT_SHA}
          cache: true
          
  deploy:
    runs-on:
      group: platform  # 部署步骤使用平台运行时
    depends-on: [docker-build]
    steps:
      - name: 部署到 K8s
        type: K8S_DEPLOY
        with:
          environment: production
          image: registry.example.com/myapp:${COMMIT_SHA}
```

## 常见问题

### 1. Pod 创建失败

**问题**：Pod 一直处于 Pending 状态

**排查**：
```bash
kubectl describe pod <pod-name> -n devops-build
```

**可能原因**：
- 资源不足（CPU/内存）
- PVC 无法绑定
- 镜像拉取失败
- 节点 Taints 限制

### 2. 镜像拉取失败

**问题**：ImagePullBackOff

**解决**：
1. 检查镜像名称是否正确
2. 配置镜像拉取凭证
3. 使用国内镜像源

```yaml
# 使用阿里云镜像源
default-image: registry.cn-hangzhou.aliyuncs.com/library/maven:3.8-openjdk-17
kaniko-image: registry.cn-hangzhou.aliyuncs.com/kaniko-project/executor:v1.9.0
```

### 3. 工作空间存储问题

**问题**：PVC 无法创建或挂载

**解决**：

**方案 A**: 使用 EmptyDir（适合临时构建）
```yaml
workspace-storage:
  type: EMPTY_DIR
  storage-size: 20Gi
```

**方案 B**: 使用 HostPath（单节点测试）
- 需要修改代码支持 HostPath 类型

**方案 C**: 部署 NFS Provisioner
```bash
helm install nfs-subdir-external-provisioner \
  nfs-subdir-external-provisioner/nfs-subdir-external-provisioner \
  --set nfs.server=<nfs-server-ip> \
  --set nfs.path=/exported/path
```

### 4. Kaniko 构建失败

**问题**：Kaniko 无法推送镜像

**解决**：
1. 配置镜像仓库凭证
2. 使用 `--insecure` 参数（测试环境）
3. 检查网络连接

### 5. Pod 清理不及时

**问题**：大量过期 Pod 占用资源

**解决**：
1. 调整清理策略
```yaml
pod-cleanup:
  enabled: true
  retention-hours: 6  # 缩短保留时间
```

2. 手动清理
```bash
kubectl delete pod -n devops-build -l app=pipeline-build --field-selector status.phase=Succeeded
kubectl delete pod -n devops-build -l app=pipeline-build --field-selector status.phase=Failed
```

## 性能优化建议

### 1. 缓存优化

```yaml
# 使用缓存加速 Maven 构建
jobs:
  build:
    cache:
      paths:
        - /root/.m2/repository
      key: maven-${APPLICATION_ID}
```

### 2. 资源配额调整

根据实际构建任务调整资源配额：

```yaml
resource-quota:
  # 小型项目
  cpu-request: 500m
  memory-request: 1Gi
  
  # 大型项目
  cpu-request: 2000m
  memory-request: 4Gi
```

### 3. 并发控制

在 K8s 层面限制并发 Pod 数量，避免资源耗尽：

```yaml
apiVersion: v1
kind: ResourceQuota
metadata:
  name: devops-build-quota
  namespace: devops-build
spec:
  hard:
    pods: "50"
    requests.cpu: "50"
    requests.memory: 100Gi
    limits.cpu: "100"
    limits.memory: 200Gi
```

## 监控和告警

### 1. Prometheus 监控

监控指标：
- Pod 创建成功率
- 平均构建时长
- 资源使用率
- Pod 清理情况

### 2. Grafana 仪表盘

关键面板：
- 构建 Pod 数量趋势
- 资源使用热力图
- 构建失败率
- 平均等待时间

### 3. 告警规则

```yaml
# Pod 创建失败告警
alert: PipelineBuildPodCreateFailed
expr: rate(pipeline_build_pod_create_failed_total[5m]) > 0.1

# 资源不足告警
alert: PipelineBuildResourceExhausted
expr: kube_resourcequota_used / kube_resourcequota_hard > 0.9
```

## 安全加固

### 1. Pod Security Policy

```yaml
apiVersion: policy/v1beta1
kind: PodSecurityPolicy
metadata:
  name: pipeline-build-psp
spec:
  privileged: false
  allowPrivilegeEscalation: false
  requiredDropCapabilities:
    - ALL
  volumes:
    - 'persistentVolumeClaim'
    - 'emptyDir'
    - 'configMap'
    - 'secret'
  runAsUser:
    rule: 'MustRunAsNonRoot'
  seLinux:
    rule: 'RunAsAny'
  fsGroup:
    rule: 'RunAsAny'
```

### 2. Network Policy

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: pipeline-build-network-policy
  namespace: devops-build
spec:
  podSelector:
    matchLabels:
      app: pipeline-build
  policyTypes:
    - Egress
  egress:
    # 允许访问镜像仓库
    - to:
      - namespaceSelector: {}
      ports:
      - protocol: TCP
        port: 443
    # 允许访问 DNS
    - to:
      - namespaceSelector: {}
      ports:
      - protocol: UDP
        port: 53
```

## 迁移指南

### 从 Docker 迁移到 K8s

1. **评估现有流水线**
   - 统计 Docker 构建任务数量
   - 评估资源需求
   - 识别特殊依赖

2. **灰度发布**
   ```yaml
   # 先配置部分项目使用 K8s
   jobs:
     build:
       runs-on:
         group: kubernetes/build-cluster  # 指定使用 K8s
   ```

3. **监控对比**
   - 构建时长对比
   - 成功率对比
   - 资源消耗对比

4. **全量切换**
   ```yaml
   # 修改全局默认配置
   devops:
     config:
       build:
         default-executor: KUBERNETES
   ```
