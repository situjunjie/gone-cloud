# K8s构建集群重构 - 快速部署指南

## 概述

重构后的Pipeline执行架构：
- ✅ **所有环境操作委派K8s执行**：git、命令、Docker构建
- ✅ **平台服务只做编排**：审批、状态管理、K8s API调用
- ✅ **新增Checkout step**：显式的git clone步骤
- ✅ **Git凭证通过Secret注入**：安全管理凭证

## 部署步骤

### 1. 构建Pipeline构建镜像

构建包含git和构建工具的Docker镜像：

```bash
cd /Users/situjunjie/projects/gone-cloud/.devops
./build-builder-image.sh
```

镜像包含：
- Git
- OpenJDK 17 + Maven
- Node.js 20 + npm/yarn/pnpm
- Docker CLI
- kubectl

### 2. 配置Git凭证

在K8s构建集群中创建Secret：

```bash
# 方式1: 直接创建
kubectl create secret generic git-credentials \
  --from-literal=username='your-git-username' \
  --from-literal=password='your-git-token' \
  --from-literal=user-name='Pipeline Bot' \
  --from-literal=user-email='bot@pipeline.local' \
  -n devops-build

# 方式2: 使用YAML文件
# 编辑 k8s-git-credentials-secret.yaml，填入实际凭证
kubectl apply -f k8s-git-credentials-secret.yaml
```

**注意**：建议使用Personal Access Token而不是密码。

### 3. 更新应用配置

在 `application-devops-k8s.yml` 中配置构建镜像：

```yaml
devops:
  config:
    build:
      cluster:
        environment-id: 1
        namespace: devops-build
        default-image: registry.gigimed.cn/devops/builder:latest  # 使用新构建的镜像
        kaniko-image: gcr.io/kaniko-project/executor:latest
        
        workspace-storage:
          type: SHARED_PVC
          shared-pvc-name: devops-workspace
          storage-size: 50Gi
        
        resource-quota:
          cpu-request: "500m"
          cpu-limit: "2000m"
          memory-request: "1Gi"
          memory-limit: "4Gi"
        
        pod-cleanup:
          strategy: IMMEDIATE
```

### 4. 测试Pipeline

使用示例Pipeline测试：

```bash
# 查看示例pipeline
cat k8s-build-example-pipeline.yaml

# 在DevOps平台创建pipeline，使用上述YAML内容
# 或通过API导入
```

示例Pipeline包含：
1. **Checkout step** - 克隆代码
2. **Command step** - 显示信息、编译
3. **KanikoBuild step** - 构建镜像
4. **Approval step** - 审批（平台执行）
5. **K8sDeploy step** - 部署（平台执行）

## 新增的Step类型

### 1. CodeMerge - 代码检出与合并

**重要变化**：CodeMerge现在在K8s中执行，可用于代码检出和合并：

```yaml
steps:
  code_merge_step:
    name: 代码检出
    step: CodeMerge
    with:
      baseBranch: master-gigi
      targetBranch: master-gigi
      branches: []              # 空数组表示只检出，不做合并
      pushOnSuccess: false      # 不推送
```

用于多分支合并：
```yaml
steps:
  merge_step:
    name: 代码合并
    step: CodeMerge
    with:
      baseBranch: develop
      targetBranch: release/v1.0
      branches:
        - feature/a
        - feature/b
      pushOnSuccess: true
```

**特性**：
- 在K8s Pod中执行git操作
- 支持单纯代码检出（branches为空数组）
- 支持多分支合并
- 自动使用K8s Secret中的git凭证
- 遇到冲突时挂起pipeline

### 2. KanikoBuild - Kaniko镜像构建

使用Kaniko在K8s中构建镜像（无需Docker daemon）：

```yaml
steps:
  build_step:
    name: 构建镜像
    step: KanikoBuild
    with:
      dockerfile: Dockerfile
      context: .
      imageTag: registry.example.com/app:v1.0.0
      buildArgs:
        - JAR_FILE=target/app.jar
        - VERSION=1.0.0
      cache: true                    # 启用缓存
      cacheRepo: registry.example.com/cache  # 缓存仓库
      registry: registry.example.com
      registryUsername: user
      registryPassword: pass
      target: production             # 多阶段构建目标
      timeoutSeconds: 3600
```

### 4. Command - 命令执行

在K8s Pod中执行shell命令：

```yaml
steps:
  compile_step:
    name: 编译项目
    step: Command
    with:
      run: |
        mvn clean package
      env:
        - MAVEN_OPTS=-Xmx2g
```

## 执行流程对比

### 重构前

```
Platform Service:
  ├─ Git操作 (本地执行)
  ├─ Docker构建 (本地daemon)
  ├─ 命令执行 (本地/Docker)
  └─ 审批 (平台)
```

### 重构后

```
Platform Service:
  ├─ 审批 ✅ (平台执行)
  ├─ K8s API调用 ✅ (平台执行)
  └─ 状态管理 ✅ (平台执行)

K8s Build Cluster:
  ├─ Git Checkout ✅ (K8s Pod执行)
  ├─ Git Merge ✅ (K8s Pod执行)
  ├─ 命令执行 ✅ (K8s Pod执行)
  └─ Kaniko构建 ✅ (K8s Pod执行)
```

## 验证清单

部署后验证：

- [ ] 构建镜像已推送到仓库
- [ ] K8s Secret git-credentials已创建
- [ ] 应用配置中default-image指向新镜像
- [ ] 创建测试pipeline
- [ ] Checkout step成功克隆代码
- [ ] Command step成功执行命令
- [ ] KanikoBuild成功构建镜像
- [ ] 查看Pod日志确认执行流程
- [ ] 验证workspace在job间共享

## 常见问题

### 1. Git克隆失败

**症状**：Checkout step报错 "fatal: could not read Username"

**解决**：
```bash
# 检查Secret是否存在
kubectl get secret git-credentials -n devops-build

# 检查Secret内容
kubectl get secret git-credentials -n devops-build -o yaml

# 重新创建Secret
kubectl delete secret git-credentials -n devops-build
kubectl create secret generic git-credentials --from-literal=username='xxx' --from-literal=password='xxx' -n devops-build
```

### 2. 镜像拉取失败

**症状**：Pod无法启动，报错 "ImagePullBackOff"

**解决**：
```bash
# 检查镜像是否存在
docker pull registry.gigimed.cn/devops/builder:latest

# 检查K8s是否配置了ImagePullSecret
kubectl get secret -n devops-build | grep registry

# 如需创建ImagePullSecret
kubectl create secret docker-registry regcred \
  --docker-server=registry.gigimed.cn \
  --docker-username=xxx \
  --docker-password=xxx \
  -n devops-build
```

### 3. 工作区不共享

**症状**：第二个job看不到第一个job的文件

**解决**：
- 确认使用SHARED_PVC存储类型
- 确认PVC已创建且可用
- 检查两个job的runsOn配置相同

```bash
# 检查PVC
kubectl get pvc devops-workspace -n devops-build

# 查看PVC绑定状态
kubectl describe pvc devops-workspace -n devops-build
```

### 4. Kaniko构建失败

**症状**：KanikoBuild step报错推送镜像失败

**解决**：
- 在step中配置registry凭证
- 或在K8s中创建docker-registry Secret

```yaml
steps:
  build_step:
    name: 构建镜像
    step: KanikoBuild
    with:
      imageTag: registry.gigimed.cn/app:v1.0
      registryUsername: ${secrets.REGISTRY_USER}
      registryPassword: ${secrets.REGISTRY_PASS}
```

## 下一步

1. **迁移现有pipeline**：将现有pipeline更新为新格式，添加Checkout step
2. **优化构建镜像**：根据实际需求定制构建工具链
3. **配置缓存**：启用Maven/npm缓存加速构建
4. **监控和告警**：接入K8s监控，跟踪Pod资源使用

## 参考文档

- [完整重构方案](../REFACTOR_PLAN.md)
- [K8s构建集群指南](../K8S_BUILD_CLUSTER_GUIDE.md)
- [Kaniko官方文档](https://github.com/GoogleContainerTools/kaniko)
