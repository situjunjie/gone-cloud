# 单节点 K8s 集群快速开始指南

> 本文为早期部署草案。当前权限还需允许服务端 kubeconfig 身份更新 Pod/PVC 的清理标记（patch/update）；当前 DSL、缓存约束和验收状态见[修复记录](../.trellis/tasks/08-31-devops-global-config-build-cluster/notes.md)。

## 🎯 适用场景
- 单物理节点的 K8s 集群（如开发测试环境）
- minikube / k3s / Docker Desktop Kubernetes
- 快速测试和验证

## 🚀 3种存储方案对比

| 方案 | 复杂度 | 持久化 | 性能 | 推荐场景 |
|-----|--------|--------|------|---------|
| **EmptyDir** | ⭐ 最简单 | ❌ 否 | ⭐⭐⭐ 最快 | 快速测试 |
| **HostPath PV** | ⭐⭐ 简单 | ✅ 是 | ⭐⭐⭐ 快 | 开发环境 |
| **Local Path** | ⭐⭐⭐ 中等 | ✅ 是 | ⭐⭐ 中 | 类生产环境 |

---

## 方案1: EmptyDir（推荐快速测试）⭐⭐⭐

### 特点
- **0分钟准备**，无需创建任何 K8s 资源
- Pod 删除后数据丢失（适合临时构建）
- 性能最好（内存或本地磁盘）

### 1. 创建命名空间和权限

```bash
kubectl apply -f - <<EOF
---
apiVersion: v1
kind: Namespace
metadata:
  name: devops-build
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
    verbs: ["create", "get", "list", "delete", "watch"]
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
```

### 2. 配置 application.yml

```yaml
devops:
  config:
    build:
      default-executor: KUBERNETES
      cluster:
        environment-id: <你的环境ID>
        namespace: devops-build
        default-image: maven:3.8-openjdk-17
        
        # 使用 EmptyDir，无需创建 PVC
        workspace-storage:
          type: EMPTY_DIR
          storage-size: 20Gi  # 限制大小，防止占满磁盘
        
        resource-quota:
          cpu-request: 500m
          memory-request: 1Gi
          cpu-limit: 1000m
          memory-limit: 2Gi
        
        pod-cleanup:
          strategy: IMMEDIATE  # 立即清理
```

### 3. 验证

```bash
# 应该只看到 ServiceAccount、Role、RoleBinding，没有 PVC
kubectl get all,sa,role,rolebinding -n devops-build

# 启动应用，触发一个测试构建
# Pod 会自动使用 EmptyDir
```

✅ **优点：** 最快开始测试  
⚠️ **注意：** 数据不持久化，每次构建都是全新环境

---

## 方案2: HostPath PV（推荐开发环境）⭐⭐

### 特点
- **2分钟准备**，创建一个 PV 和 PVC
- 数据持久化到节点本地目录
- 支持多个 Job 共享工作空间

### 1. 在节点上创建目录

```bash
# SSH 到你的 K8s 节点，或者如果是本地集群直接执行
sudo mkdir -p /data/devops/pipeline-workspace
sudo chmod 777 /data/devops/pipeline-workspace

# 如果是 Docker Desktop / minikube
# Docker Desktop: 自动挂载宿主机目录
# minikube: minikube ssh 进入后执行
```

### 2. 创建 K8s 资源

```bash
kubectl apply -f - <<EOF
---
apiVersion: v1
kind: Namespace
metadata:
  name: devops-build
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
    verbs: ["create", "get", "list", "delete", "watch"]
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
---
# 创建 PV（持久卷）
apiVersion: v1
kind: PersistentVolume
metadata:
  name: pipeline-workspace-pv
spec:
  capacity:
    storage: 100Gi
  accessModes:
    - ReadWriteMany  # 单节点可以用 RWX
  persistentVolumeReclaimPolicy: Retain
  hostPath:
    path: /data/devops/pipeline-workspace
    type: DirectoryOrCreate
---
# 创建 PVC（持久卷申请）
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: pipeline-shared-workspace
  namespace: devops-build
spec:
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 100Gi
  # 不指定 storageClassName，自动绑定上面的 PV
EOF
```

### 3. 验证 PVC 绑定

```bash
# 检查 PV 和 PVC 状态
kubectl get pv,pvc -n devops-build

# 应该看到：
# pv/pipeline-workspace-pv    100Gi    RWX    Retain    Bound
# pvc/pipeline-shared-workspace    Bound    pipeline-workspace-pv    100Gi    RWX
```

### 4. 配置 application.yml

```yaml
devops:
  config:
    build:
      default-executor: KUBERNETES
      cluster:
        environment-id: <你的环境ID>
        namespace: devops-build
        default-image: maven:3.8-openjdk-17
        
        # 使用共享 PVC
        workspace-storage:
          type: SHARED_PVC
          shared-pvc-name: pipeline-shared-workspace
        
        resource-quota:
          cpu-request: 500m
          memory-request: 1Gi
          cpu-limit: 1000m
          memory-limit: 2Gi
        
        pod-cleanup:
          strategy: IMMEDIATE
```

### 5. 测试

```bash
# 查看工作空间目录结构
ls -la /data/devops/pipeline-workspace/

# 触发构建后，会看到类似结构：
# runs/
#   ├── 1/    # Run ID 1 的工作空间
#   ├── 2/    # Run ID 2 的工作空间
#   └── 3/
```

✅ **优点：** 数据持久化，性能好  
✅ **适合：** 开发环境、需要保留构建产物

---

## 方案3: Local Path Provisioner（推荐类生产）⭐

### 特点
- **5分钟准备**，安装一个 Provisioner
- 自动管理本地存储
- 支持动态创建 PV

### 1. 安装 Local Path Provisioner

```bash
# 安装
kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/v0.0.24/deploy/local-path-storage.yaml

# 等待 Pod 运行
kubectl wait --for=condition=ready pod -l app=local-path-provisioner -n local-path-storage --timeout=60s

# 验证 StorageClass
kubectl get storageclass
# NAME                   PROVISIONER             RECLAIMPOLICY
# local-path (default)   rancher.io/local-path   Delete
```

### 2. 创建 K8s 资源

```bash
kubectl apply -f - <<EOF
---
apiVersion: v1
kind: Namespace
metadata:
  name: devops-build
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
    verbs: ["create", "get", "list", "delete", "watch"]
  - apiGroups: [""]
    resources: ["persistentvolumeclaims"]
    verbs: ["create", "get", "list", "delete"]
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
---
# 创建 PVC（会自动创建 PV）
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: pipeline-shared-workspace
  namespace: devops-build
spec:
  accessModes:
    - ReadWriteOnce  # local-path 只支持 RWO
  storageClassName: local-path
  resources:
    requests:
      storage: 100Gi
EOF
```

### 3. 验证

```bash
# 查看 PVC 和自动创建的 PV
kubectl get pvc,pv -n devops-build

# PVC 应该是 Bound 状态
```

### 4. 配置 application.yml

```yaml
devops:
  config:
    build:
      default-executor: KUBERNETES
      cluster:
        environment-id: <你的环境ID>
        namespace: devops-build
        default-image: maven:3.8-openjdk-17
        
        # 使用 local-path 的共享 PVC
        workspace-storage:
          type: SHARED_PVC
          shared-pvc-name: pipeline-shared-workspace
          storage-class-name: local-path
        
        resource-quota:
          cpu-request: 500m
          memory-request: 1Gi
          cpu-limit: 1000m
          memory-limit: 2Gi
        
        pod-cleanup:
          strategy: IMMEDIATE
```

✅ **优点：** 自动管理，接近生产环境  
⚠️ **注意：** local-path 只支持 ReadWriteOnce（单节点不影响）

---

## 🎯 推荐选择

### 场景1: 快速测试功能
👉 **选择 EmptyDir**
- 配置最简单
- 0依赖
- 5分钟开始测试

### 场景2: 开发调试
👉 **选择 HostPath PV**
- 数据持久化
- 便于查看构建产物
- 性能好

### 场景3: 长期使用
👉 **选择 Local Path Provisioner**
- 更规范
- 自动管理
- 易于扩展

---

## 🧪 快速验证脚本

### 验证 EmptyDir 方案

```bash
#!/bin/bash
echo "✅ EmptyDir 方案无需创建 PVC"
echo "📦 检查命名空间..."
kubectl get ns devops-build || echo "❌ 请先创建命名空间"
echo "🎉 可以直接配置应用并测试！"
```

### 验证 HostPath 方案

```bash
#!/bin/bash
echo "🔍 验证 HostPath 方案..."

# 检查命名空间
kubectl get ns devops-build || { echo "❌ 命名空间不存在"; exit 1; }

# 检查 PV
kubectl get pv pipeline-workspace-pv || { echo "❌ PV 不存在"; exit 1; }

# 检查 PVC 绑定状态
PVC_STATUS=$(kubectl get pvc pipeline-shared-workspace -n devops-build -o jsonpath='{.status.phase}')
if [ "$PVC_STATUS" == "Bound" ]; then
    echo "✅ PVC 已绑定"
else
    echo "❌ PVC 未绑定，当前状态: $PVC_STATUS"
    kubectl describe pvc pipeline-shared-workspace -n devops-build
    exit 1
fi

echo "🎉 验证通过，可以开始测试！"
```

### 验证 Local Path 方案

```bash
#!/bin/bash
echo "🔍 验证 Local Path 方案..."

# 检查 Provisioner
kubectl get pod -n local-path-storage -l app=local-path-provisioner || {
    echo "❌ Local Path Provisioner 未安装"
    exit 1
}

# 检查 StorageClass
kubectl get sc local-path || { echo "❌ StorageClass 不存在"; exit 1; }

# 检查 PVC
PVC_STATUS=$(kubectl get pvc pipeline-shared-workspace -n devops-build -o jsonpath='{.status.phase}')
if [ "$PVC_STATUS" == "Bound" ]; then
    echo "✅ PVC 已绑定"
else
    echo "❌ PVC 未绑定，当前状态: $PVC_STATUS"
    exit 1
fi

echo "🎉 验证通过，可以开始测试！"
```

---

## 📝 常见问题

### Q: PVC 一直 Pending 怎么办？

```bash
# 查看详情
kubectl describe pvc pipeline-shared-workspace -n devops-build

# 常见原因：
# 1. 没有可用的 PV -> 检查 PV 是否创建
# 2. StorageClass 不存在 -> 改用 EmptyDir 或创建 HostPath PV
# 3. 节点空间不足 -> 清理磁盘空间
```

### Q: Pod 无法挂载卷？

```bash
# 查看 Pod 事件
kubectl describe pod <pod-name> -n devops-build

# 如果是权限问题，检查节点目录权限
sudo chmod 777 /data/devops/pipeline-workspace
```

### Q: 想换存储方案怎么办？

```bash
# 1. 删除旧的 PVC（如果有）
kubectl delete pvc pipeline-shared-workspace -n devops-build

# 2. 修改 application.yml 配置
# 3. 重启应用
# 4. 创建新的 PVC（如果需要）
```

---

## 🚀 下一步

选择好存储方案后，参考 [K8S_BUILD_TEST_PLAN.md](./K8S_BUILD_TEST_PLAN.md) 开始测试！
