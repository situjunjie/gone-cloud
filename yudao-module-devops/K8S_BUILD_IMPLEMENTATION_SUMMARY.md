# K8s 构建集群实现总结

> 本文为早期实现草案，完成度、镜像版本和流水线示例已过时。以[2026-09-07 修复与验证记录](../.trellis/tasks/08-31-devops-global-config-build-cluster/notes.md)及 `application-devops-k8s-example.yml` 为准；尚未完成真实集群验收。

## ✅ 已完成的功能

### 1. 核心组件实现

#### 1.1 配置类
- **DevopsConfig.java** - 完整的配置结构
  - 支持 KUBERNETES/DOCKER 执行器选择
  - 3种存储策略：SHARED_PVC, DYNAMIC_PVC, EMPTY_DIR
  - 资源配额管理
  - 智能Pod清理策略

#### 1.2 运行时管理
- **K8sPipelineJobRuntimeManager.java** - K8s运行时管理器
  - Pod创建和生命周期管理
  - 存储卷挂载（支持3种存储方式）
  - 等待Pod就绪
  - 智能清理策略（IMMEDIATE/RETAIN）

- **K8sCommandExecutor.java** - K8s命令执行器
  - 通过K8s API执行命令
  - 流式日志收集
  - 支持取消任务
  - 环境变量注入

#### 1.3 工厂和调度
- **PipelineJobRuntimeManagerFactory.java** - 执行器工厂
  - 根据配置自动选择执行器
  - 支持Job级别覆盖（runs-on）
  - 统一的命令执行器接口

#### 1.4 Kaniko构建
- **KanikoDockerBuildStepHandler.java** - Kaniko镜像构建
  - 无需Docker Daemon
  - 支持构建参数、缓存
  - 完整的构建日志
  - 构建状态管理

#### 1.5 自动化运维
- **K8sBuildPodCleanupJob.java** - Pod清理定时任务
  - 每15分钟执行
  - 只清理标记的失败Pod
  - 成功的Pod立即清理

#### 1.6 数据模型
- **PipelineJobRuntime.java** - 添加 metadata 字段
  - 支持扩展元数据存储
  - K8s环境信息传递

### 2. 错误码
- 新增4个K8s构建相关错误码
  - DEVOPS_BUILD_CLUSTER_NOT_CONFIGURED
  - DEVOPS_BUILD_K8S_POD_CREATE_FAIL
  - DEVOPS_BUILD_K8S_POD_NOT_READY
  - DEVOPS_BUILD_K8S_COMMAND_EXEC_FAIL

## 📝 配置说明

### 最简配置（EmptyDir）

```yaml
devops:
  config:
    build:
      default-executor: KUBERNETES
      cluster:
        environment-id: 1
        namespace: devops-build
        workspace-storage:
          type: EMPTY_DIR
          storage-size: 20Gi
        pod-cleanup:
          strategy: IMMEDIATE
```

### 完整配置（生产环境）

```yaml
devops:
  config:
    build:
      # 默认执行器
      default-executor: KUBERNETES
      
      cluster:
        # K8s环境ID（系统中创建的环境）
        environment-id: 1
        
        # 构建命名空间
        namespace: devops-build
        
        # 默认构建镜像
        default-image: maven:3.8-openjdk-17
        
        # Kaniko执行器镜像
        kaniko-image: gcr.io/kaniko-project/executor:v1.9.0
        
        # 工作空间存储
        workspace-storage:
          type: SHARED_PVC  # SHARED_PVC, DYNAMIC_PVC, EMPTY_DIR
          shared-pvc-name: pipeline-shared-workspace
          storage-class-name: local-path
          storage-size: 10Gi
        
        # 资源配额
        resource-quota:
          cpu-request: 1000m
          memory-request: 2Gi
          cpu-limit: 2000m
          memory-limit: 4Gi
        
        # Pod清理策略
        pod-cleanup:
          strategy: IMMEDIATE  # IMMEDIATE 或 RETAIN
          retention-minutes: 30  # RETAIN模式下的保留时长
          retain-failed-pods: true  # 是否保留失败的Pod
```

## 🎯 智能Pod清理策略

### IMMEDIATE 模式（推荐生产环境）
- 任务完成后**立即删除**Pod
- 成功和失败的Pod都立即清理
- 无残留资源
- 无需定时任务

### RETAIN 模式（推荐测试环境）
- 成功的Pod **立即删除**
- 失败的Pod **保留指定时间**（默认30分钟）
- 便于排查问题和调试
- 定时任务每15分钟清理过期的失败Pod

## 📋 使用示例

### 示例1: 基础命令执行

```yaml
jobs:
  test:
    runs-on:
      group: kubernetes/build-cluster
      container: alpine:latest
    steps:
      - name: Hello K8s
        type: COMMAND
        with:
          run: |
            echo "Hello from K8s!"
            pwd
            whoami
```

### 示例2: Maven构建

```yaml
jobs:
  build:
    runs-on:
      group: kubernetes/build-cluster
      container: maven:3.8-openjdk-17
    steps:
      - name: Checkout
        type: CODE_CHECKOUT
        
      - name: Build
        type: COMMAND
        with:
          run: mvn clean package -DskipTests
```

### 示例3: Kaniko镜像构建

```yaml
jobs:
  docker-build:
    runs-on:
      group: kubernetes/build-cluster
      container: gcr.io/kaniko-project/executor:v1.9.0
    steps:
      - name: Build Image
        type: KANIKO_DOCKER_BUILD
        with:
          dockerfile: Dockerfile
          context: .
          image: registry.example.com/myapp:${COMMIT_SHA}
          cache: true
          buildArgs:
            APP_VERSION: ${COMMIT_SHA}
```

## 🚀 部署步骤

### 第1步: 准备K8s集群

```bash
# 创建命名空间和RBAC
kubectl apply -f - <<EOF
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
EOF
```

### 第2步: 创建存储（可选）

#### EmptyDir模式 - 无需创建

#### HostPath模式
```bash
kubectl apply -f - <<EOF
apiVersion: v1
kind: PersistentVolume
metadata:
  name: pipeline-workspace-pv
spec:
  capacity:
    storage: 100Gi
  accessModes:
    - ReadWriteMany
  persistentVolumeReclaimPolicy: Retain
  hostPath:
    path: /data/devops/pipeline-workspace
    type: DirectoryOrCreate
---
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
EOF
```

### 第3步: 配置系统

1. 登录系统，创建K8s环境
2. 上传kubeconfig
3. 配置application.yml
4. 重启应用

### 第4步: 测试

参考 `K8S_BUILD_TEST_PLAN.md` 进行测试

## 📚 文档清单

1. **K8S_BUILD_CLUSTER_GUIDE.md** - 完整的部署和使用指南
   - K8s集群准备
   - Pipeline配置示例
   - 常见问题排查
   - 性能优化建议

2. **K8S_BUILD_TEST_PLAN.md** - 详细的测试计划
   - 8个测试场景
   - 性能测试方法
   - 故障排查步骤
   - 测试报告模板

3. **K8S_BUILD_SINGLE_NODE_GUIDE.md** - 单节点快速开始
   - 3种存储方案对比
   - 快速开始步骤
   - 验证脚本

## 🔧 技术要点

### 1. 存储方案
- **EmptyDir**: 最简单，无持久化，适合测试
- **HostPath**: 持久化到节点，适合单节点开发环境
- **PVC**: 标准方案，适合生产环境

### 2. 资源管理
- CPU/内存配额限制
- 支持自定义资源需求
- 防止资源滥用

### 3. 日志收集
- 实时流式日志输出
- 支持标准输出和错误输出
- 日志行数限制和截断

### 4. 错误处理
- 超时控制
- 取消机制
- 详细的错误信息

## ⚠️ 注意事项

1. **首次测试建议使用EmptyDir**
   - 配置最简单
   - 快速验证功能
   - 无外部依赖

2. **单节点集群的限制**
   - local-path只支持ReadWriteOnce
   - 使用HostPath可以支持ReadWriteMany

3. **镜像拉取**
   - 国内环境建议配置镜像加速
   - 或使用阿里云镜像源

4. **Pod清理**
   - 生产环境建议用IMMEDIATE
   - 测试环境建议用RETAIN便于调试

## 🐛 已修复的问题

1. ✅ PipelineJobRuntime缺少metadata字段
2. ✅ K8sCommandExecutor的InterruptedException处理
3. ✅ 添加详细的日志输出
4. ✅ 完善错误处理

## 📞 下一步

1. **编译项目** - 解决Maven依赖问题
2. **部署测试** - 按照文档部署到K8s集群
3. **功能验证** - 执行测试计划
4. **性能优化** - 根据测试结果调优

---

**创建日期**: 2026-09-01  
**版本**: v1.0  
**状态**: ✅ 开发完成，待测试
