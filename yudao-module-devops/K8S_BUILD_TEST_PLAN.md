# K8s 构建集群测试方案

> 2026-09-07：本地 Maven reactor 验证通过，DevOps server 466 tests，0 failures/errors。以下真实集群检查尚未执行；当前 DSL 与限制见[任务记录](../.trellis/tasks/08-31-devops-global-config-build-cluster/notes.md)。RBAC 必须授予平台服务端 kubeconfig 使用的身份，仅创建此处 ServiceAccount 不会自动切换身份。

## 📋 测试准备清单

### 1. 环境检查
- [ ] K8s 集群可访问
- [ ] kubectl 配置正确
- [ ] 应用已部署并启动
- [ ] DevOps 模块配置完成

### 2. K8s 集群资源准备

```bash
# 1. 创建命名空间
kubectl apply -f - <<EOF
apiVersion: v1
kind: Namespace
metadata:
  name: devops-build
  labels:
    name: devops-build
EOF

# 2. 创建 RBAC 权限
kubectl apply -f - <<EOF
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
EOF

# 3. 创建共享 PVC（如使用 SHARED_PVC 策略）
kubectl apply -f - <<EOF
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: pipeline-shared-workspace
  namespace: devops-build
spec:
  accessModes:
    - ReadWriteMany
  storageClassName: nfs-client  # 根据你的集群修改
  resources:
    requests:
      storage: 100Gi
EOF

# 4. 验证资源创建
kubectl get ns devops-build
kubectl get sa,role,rolebinding -n devops-build
kubectl get pvc -n devops-build
```

## 🧪 测试场景

### 场景 1: 基础功能测试

#### 1.1 配置 K8s 环境

**操作步骤：**
1. 登录系统
2. 进入「环境管理」
3. 创建新环境
   - 名称: `构建集群测试`
   - 类型: `Kubernetes`
   - 上传 kubeconfig 文件
   - Namespace: `devops-build`
4. 测试连接

**预期结果：**
- ✅ 连接成功
- ✅ 显示 Namespace 数量

#### 1.2 配置 DevOps 构建集群

**修改配置文件：**
```yaml
# application.yml
devops:
  config:
    build:
      default-executor: KUBERNETES
      cluster:
        environment-id: <刚创建的环境ID>
        namespace: devops-build
        default-image: maven:3.8-openjdk-17
        workspace-storage:
          type: SHARED_PVC
          shared-pvc-name: pipeline-shared-workspace
        resource-quota:
          cpu-request: 500m
          memory-request: 1Gi
          cpu-limit: 1000m
          memory-limit: 2Gi
        pod-cleanup:
          strategy: IMMEDIATE  # 测试时使用立即清理
```

**验证：**
```bash
# 重启应用
# 查看日志确认配置加载成功
```

### 场景 2: 简单命令执行测试

#### 2.1 创建测试 Pipeline

**Pipeline YAML：**
```yaml
name: test-k8s-command
stages:
  test_stage:
    name: 测试阶段
    jobs:
      test_job:
        name: 测试基础命令
        runsOn:
          group: kubernetes/build-cluster
          container: alpine:latest
        steps:
          test_command_step:
            name: 测试基础命令
            step: Command
            with:
              run: |
                echo "Hello from K8s Pod!"
                pwd
                whoami
                df -h
                date
```

**操作步骤：**
1. 创建应用和流水线
2. 配置上述 Pipeline
3. 触发运行

**预期结果：**
- ✅ Pod 创建成功
- ✅ 命令执行成功
- ✅ 日志正常输出
- ✅ Pod 自动清理（IMMEDIATE 策略）

**验证命令：**
```bash
# 监控 Pod 创建和删除
watch -n 1 'kubectl get pods -n devops-build'

# 查看 Pod 详情（快速执行，Pod 可能已删除）
kubectl describe pod -n devops-build -l app=pipeline-build

# 查看 Pod 日志
kubectl logs -n devops-build -l app=pipeline-build --tail=50
```

### 场景 3: Maven 构建测试

#### 3.1 Java 项目构建

**Pipeline YAML：**
```yaml
name: test-maven-build
sources:
  app_repo:
    type: gitlab
    name: Application Repository
    endpoint: https://gitlab.gigimed.cn/gigi/gone-cloud.git
    branch: master-gigi
stages:
  build_stage:
    name: Maven 构建
    jobs:
      maven_build_job:
        name: Maven 构建
        runsOn:
          group: kubernetes/build-cluster
          container: maven:3.8-openjdk-17
        timeoutSeconds: 1800
        steps:
          code_merge_step:
            name: 代码检出
            step: CodeMerge
            with:
              baseBranch: master-gigi
              targetBranch: master-gigi
              branches: []
              pushOnSuccess: false
          
          maven_build_step:
            name: Maven 构建
            step: Command
            with:
              run: |
                mvn clean package -DskipTests -Dmaven.test.skip=true
                ls -lh target/
                echo "Build completed!"
              env:
                MAVEN_OPTS: "-Xmx2048m"
```

**预期结果：**
- ✅ 代码成功检出
- ✅ Maven 构建成功
- ✅ 构建日志完整
- ✅ 构建产物生成

**监控：**
```bash
# 实时查看 Pod 状态
kubectl get pods -n devops-build -w

# 查看 Pod 资源使用
kubectl top pod -n devops-build

# 进入 Pod 查看（如需调试）
kubectl exec -it <pod-name> -n devops-build -- /bin/bash
```

### 场景 4: Kaniko 镜像构建测试

#### 4.1 准备测试 Dockerfile

**创建简单的测试项目：**
```dockerfile
# Dockerfile
FROM alpine:latest
RUN echo "Hello from Kaniko!" > /hello.txt
CMD ["cat", "/hello.txt"]
```

#### 4.2 配置 Kaniko 构建 Pipeline

**Pipeline YAML：**
```yaml
name: test-kaniko-build
sources:
  app_repo:
    type: gitlab
    name: Application Repository
    endpoint: https://gitlab.gigimed.cn/gigi/gone-cloud.git
    branch: master-gigi
stages:
  build_stage:
    name: Kaniko 构建
    jobs:
      checkout_job:
        name: 代码检出
        runsOn:
          group: kubernetes/build-cluster
          container: registry.gigimed.cn/devops/builder:latest
        steps:
          code_merge_step:
            name: 代码检出
            step: CodeMerge
            with:
              baseBranch: master-gigi
              targetBranch: master-gigi
              branches: []
              pushOnSuccess: false
      
      kaniko_build_job:
        name: Kaniko 镜像构建
        needs:
          - checkout_job
        runsOn:
          group: kubernetes/build-cluster
        timeoutSeconds: 1800
        steps:
          kaniko_build_step:
            name: 构建 Docker 镜像
            step: KanikoBuild
            with:
              dockerfile: Dockerfile
              context: .
              imageTag: registry.gigimed.cn/test/test-kaniko:${PIPELINE_RUN_ID}
              cache: false
```

**预期结果：**
- ✅ Kaniko Pod 创建成功
- ✅ 镜像构建成功
- ✅ 镜像推送到仓库
- ✅ 构建日志清晰

**验证：**
```bash
# 查看镜像是否推送成功
docker pull <你的镜像仓库>/test-kaniko:<commit-sha>
docker run --rm <你的镜像仓库>/test-kaniko:<commit-sha>
```

### 场景 5: 资源配额测试

#### 5.1 测试资源限制

**Pipeline YAML：**
```yaml
name: test-resource-limits
stages:
  test_stage:
    name: 资源测试
    jobs:
      memory_test_job:
        name: 内存压力测试
        runsOn:
          group: kubernetes/build-cluster
          container: alpine:latest
        steps:
          memory_test_step:
            name: 内存压力测试
            step: Command
            with:
              run: |
                echo "Testing memory limits..."
                # 尝试分配大量内存（会受到限制）
                dd if=/dev/zero of=/tmp/test bs=1M count=3000 || echo "Memory limited as expected"
                df -h
```

**预期结果：**
- ✅ Pod 受到内存限制
- ✅ 不会影响集群其他服务
- ✅ OOM 时 Pod 被正确处理

### 场景 6: 并发构建测试

#### 6.1 同时触发多个构建

**操作：**
1. 同时触发 3-5 个构建任务
2. 观察资源分配
3. 验证互不干扰

**验证命令：**
```bash
# 查看所有构建 Pod
kubectl get pods -n devops-build -l app=pipeline-build

# 查看资源使用情况
kubectl top pods -n devops-build

# 查看事件
kubectl get events -n devops-build --sort-by='.lastTimestamp'
```

**预期结果：**
- ✅ 多个 Pod 可以并行运行
- ✅ 资源按配额分配
- ✅ 工作空间隔离正确

### 场景 7: 失败场景测试

#### 7.1 构建失败处理

**Pipeline YAML：**
```yaml
name: test-build-failure
stages:
  fail_stage:
    name: 失败测试
    jobs:
      fail_job:
        name: 故意失败的任务
        runsOn:
          group: kubernetes/build-cluster
          container: alpine:latest
        steps:
          fail_step:
            name: 故意失败
            step: Command
            with:
              run: |
                echo "This will fail"
                exit 1
```

**预期结果：**
- ✅ 构建正确标记为失败
- ✅ 错误日志完整记录
- ✅ Pod 按策略清理（IMMEDIATE）或保留（RETAIN）

#### 7.2 Pod 创建失败

**测试方法：**
1. 配置不存在的镜像
2. 配置过大的资源请求

**验证：**
```bash
# 查看 Pod 状态
kubectl describe pod <pod-name> -n devops-build

# 应该看到 ImagePullBackOff 或 Pending 状态
```

### 场景 8: 清理策略测试

#### 8.1 测试 IMMEDIATE 策略

**配置：**
```yaml
pod-cleanup:
  strategy: IMMEDIATE
```

**预期：**
- ✅ 成功的 Pod 立即删除
- ✅ 失败的 Pod 立即删除
- ✅ 不残留 Pod

#### 8.2 测试 RETAIN 策略

**配置：**
```yaml
pod-cleanup:
  strategy: RETAIN
  retention-minutes: 5
  retain-failed-pods: true
```

**预期：**
- ✅ 成功的 Pod 立即删除
- ✅ 失败的 Pod 保留 5 分钟
- ✅ 定时任务清理过期 Pod

**验证：**
```bash
# 触发一个失败的构建
# 立即查看 Pod
kubectl get pods -n devops-build

# 检查 Pod 注解
kubectl get pod <pod-name> -n devops-build -o jsonpath='{.metadata.annotations}'

# 等待 5 分钟后再次查看
# Pod 应该被定时任务清理
```

## 📊 性能测试

### 测试 1: Pod 启动时间

```bash
# 记录 Pod 从创建到 Running 的时间
kubectl get pods -n devops-build -w
```

**目标：**
- Pod 启动时间 < 30 秒（取决于镜像大小和网络）

### 测试 2: 构建吞吐量

**测试方法：**
1. 连续触发 10 个构建任务
2. 记录总耗时
3. 计算平均构建时间

**监控：**
```bash
# 统计当前运行的 Pod
kubectl get pods -n devops-build --no-headers | wc -l
```

### 测试 3: 资源使用

```bash
# 查看命名空间资源使用
kubectl top nodes
kubectl top pods -n devops-build

# 查看资源配额使用情况
kubectl describe resourcequota -n devops-build
```

## 🐛 故障排查

### 问题 1: Pod 无法创建

```bash
# 查看事件
kubectl get events -n devops-build --sort-by='.lastTimestamp'

# 查看 Pod 详情
kubectl describe pod <pod-name> -n devops-build

# 常见原因：
# - 资源不足
# - PVC 无法绑定
# - 镜像拉取失败
# - RBAC 权限不足
```

### 问题 2: 命令执行失败

```bash
# 查看 Pod 日志
kubectl logs <pod-name> -n devops-build -c builder

# 进入 Pod 调试
kubectl exec -it <pod-name> -n devops-build -- /bin/sh
```

### 问题 3: 工作空间问题

```bash
# 检查 PVC 状态
kubectl get pvc -n devops-build
kubectl describe pvc pipeline-shared-workspace -n devops-build

# 检查存储类
kubectl get sc

# 进入 Pod 检查挂载
kubectl exec -it <pod-name> -n devops-build -- df -h
```

## ✅ 测试验收标准

### 功能验收
- [ ] Pod 可以正常创建和运行
- [ ] 命令可以正常执行
- [ ] 日志可以正常收集
- [ ] Maven 构建可以成功
- [ ] Kaniko 镜像构建可以成功
- [ ] 工作空间正常工作
- [ ] 并发构建互不干扰
- [ ] 失败场景正确处理
- [ ] Pod 按策略清理

### 性能验收
- [ ] Pod 启动时间 < 30s
- [ ] 支持至少 10 个并发构建
- [ ] 资源使用在预期范围内

### 稳定性验收
- [ ] 连续运行 50 次构建无异常
- [ ] 长时间运行无资源泄漏
- [ ] 失败重试机制正常

## 📝 测试报告模板

```markdown
# K8s 构建集群测试报告

## 测试环境
- K8s 版本: 
- 节点数量: 
- 存储类型: 
- 测试时间: 

## 测试结果

### 功能测试
| 测试场景 | 测试结果 | 备注 |
|---------|---------|------|
| 基础命令执行 | ✅ / ❌ |  |
| Maven 构建 | ✅ / ❌ |  |
| Kaniko 构建 | ✅ / ❌ |  |
| 并发构建 | ✅ / ❌ |  |
| 失败处理 | ✅ / ❌ |  |
| Pod 清理 | ✅ / ❌ |  |

### 性能指标
| 指标 | 目标值 | 实际值 | 是否达标 |
|-----|-------|--------|---------|
| Pod 启动时间 | < 30s |  | ✅ / ❌ |
| 并发构建数 | ≥ 10 |  | ✅ / ❌ |
| 平均构建时间 |  |  |  |

### 问题记录
1. 问题描述
2. 复现步骤
3. 解决方案

## 结论
- [ ] 通过测试，可以上线
- [ ] 需要优化后再测试
```

## 🚀 快速测试脚本

```bash
#!/bin/bash
# quick-test.sh - K8s 构建集群快速测试脚本

set -e

echo "🚀 开始 K8s 构建集群测试..."

# 1. 检查命名空间
echo "📦 检查命名空间..."
kubectl get ns devops-build || echo "❌ 命名空间不存在"

# 2. 检查 PVC
echo "💾 检查 PVC..."
kubectl get pvc pipeline-shared-workspace -n devops-build || echo "⚠️ PVC 不存在"

# 3. 检查现有 Pod
echo "🔍 检查现有 Pod..."
kubectl get pods -n devops-build -l app=pipeline-build

# 4. 清理旧 Pod（可选）
# kubectl delete pods -n devops-build -l app=pipeline-build --force --grace-period=0

echo "✅ 测试环境检查完成！"
echo "📝 请通过系统界面触发构建任务进行测试"
```

使用方法：
```bash
chmod +x quick-test.sh
./quick-test.sh
```
