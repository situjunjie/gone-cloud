# Pipeline执行架构重构方案

## 背景

当前devops pipeline执行混合了本地执行和容器执行，存在以下问题：
1. CodeMerge等git操作在平台服务本地执行，需要本地git环境
2. 部分构建操作依赖本地Docker daemon
3. 职责不清晰，难以扩展到纯K8s构建集群

## 目标

**彻底分离执行职责**：
- **K8s构建集群**：所有环境相关操作（git、shell命令、Docker构建）
- **平台服务**：纯业务逻辑（审批、状态记录、K8s API调用）

## 重构范围

### 需要改为K8s执行的Handler

| Handler | 当前模式 | 改造后 | 说明 |
|---------|---------|--------|------|
| CodeMergeStepHandler | PLATFORM | JOB_RUNTIME | git操作委派到K8s Pod |
| CommandStepHandler | JOB_RUNTIME | JOB_RUNTIME | 已支持，无需改动 |
| KanikoDockerBuildStepHandler | JOB_RUNTIME | JOB_RUNTIME | 已支持，无需改动 |
| PrivateRegistryDockerBuildStepHandler | JOB_RUNTIME | JOB_RUNTIME | 可能需要重构为Kaniko |

### 保持平台执行的Handler

| Handler | 模式 | 说明 |
|---------|------|------|
| ApprovalStepHandler | PLATFORM | 纯业务逻辑，发起审批流程 |
| K8sDeployStepHandler | PLATFORM | 调用K8s API，不需要隔离环境 |
| K8sImageUpgradeStepHandler | PLATFORM | 调用K8s API |
| ChangePublishFinalizeStepHandler | PLATFORM | 状态更新 |

## 详细改造方案

### 1. CodeMerge改造

#### 1.1 修改CodeMergeStepHandler

```java
@Override
public StepRuntimeRequirement runtimeRequirement() {
    return StepRuntimeRequirement.JOB_RUNTIME;  // 改为JOB_RUNTIME
}

@Override
public boolean sourceWorkspaceRequired() {
    return true;  // 需要源码工作区
}
```

#### 1.2 将Git操作转换为脚本

原来的GitWorkspaceService本地调用改为生成shell脚本，在K8s Pod中执行：

```java
@Override
public StepResult handle(PipelineExecutionContext ctx) {
    // 1. 构建git操作脚本
    String gitScript = buildGitMergeScript(ctx);
    
    // 2. 通过K8s CommandExecutor执行
    PipelineCommandContext cmdCtx = PipelineCommandContext.builder()
        .runtime(ctx.getExecutorRuntime())
        .workspace(ctx.getWorkspace())
        .environmentVariables(buildGitEnv(ctx))
        .timeoutSeconds(600L)
        .build();
    
    ExecResult result = pipelineCommandExecutor.exec(cmdCtx, gitScript, logSink);
    
    // 3. 解析结果
    if (result.getExitCode() == 0) {
        return StepResult.continueWith("代码合并成功");
    } else if (result.getExitCode() == GIT_CONFLICT_EXIT_CODE) {
        // 解析冲突信息，挂起等待手动解决
        return StepResult.suspend("存在合并冲突");
    } else {
        return StepResult.fail("代码合并失败", result.getStdout());
    }
}

private String buildGitMergeScript(PipelineExecutionContext ctx) {
    StringBuilder script = new StringBuilder();
    script.append("#!/bin/bash\n");
    script.append("set -e\n");
    script.append("cd /workspace\n");
    
    // Git配置
    script.append("git config user.name \"${GIT_USER_NAME}\"\n");
    script.append("git config user.email \"${GIT_USER_EMAIL}\"\n");
    
    // Clone仓库
    script.append("git clone ${GIT_REPO_URL} .\n");
    script.append("git checkout ${TARGET_BRANCH}\n");
    
    // 执行merge
    for (String branch : branches) {
        script.append("git merge origin/").append(branch).append(" || exit 128\n");
    }
    
    // Push
    script.append("git push origin ${TARGET_BRANCH}\n");
    
    return script.toString();
}
```

#### 1.3 冲突处理

冲突场景下：
1. Git命令返回特定exit code（128）
2. 捕获冲突文件列表：`git diff --name-only --diff-filter=U`
3. 返回StepResult.suspend()挂起pipeline
4. 用户在平台手动解决冲突后，重新触发继续执行

### 2. Docker构建统一使用Kaniko

#### 2.1 废弃PrivateRegistryDockerBuildStepHandler

不再使用本地Docker daemon构建，统一使用KanikoDockerBuildStepHandler。

#### 2.2 更新文档和示例

更新pipeline YAML示例，使用Kaniko step：

```yaml
stages:
  build:
    jobs:
      build-image:
        runsOn:
          group: kubernetes/build-cluster
        steps:
          kaniko:
            step: KanikoBuild
            dockerfile: Dockerfile
            context: .
            imageTag: registry.example.com/app:${BUILD_NUMBER}
```

### 3. 源码准备重构

#### 3.1 当前问题

`PipelineSourceWorkspacePreparer`在平台本地执行git clone：

```java
// 当前在平台服务执行
gitWorkspaceService.prepareWorkspace(repoUrl, branch, workspace.getRunWorkspace());
```

#### 3.2 改造方案

源码准备也委派到K8s：

**方案A：作为隐式前置step**

在第一个需要sourceWorkspaceRequired的step前，自动注入git clone step：

```java
private void executeJob(PipelineExecutionContext jobCtx) {
    // ... 
    for (PipelineSpec.Step step : job.getSteps()) {
        PipelineStepHandler handler = stepHandlerRegistry.getHandler(step.getType());
        
        // 需要源码但尚未准备
        if (handler.sourceWorkspaceRequired() && !ctx.isSourcePrepared()) {
            // 在K8s中执行git clone
            executeGitCloneInK8s(ctx);
            ctx.setSourcePrepared(true);
        }
        
        // 执行实际step
        StepResult result = handler.handle(ctx);
        // ...
    }
}

private void executeGitCloneInK8s(PipelineExecutionContext ctx) {
    String cloneScript = buildGitCloneScript(ctx);
    PipelineCommandContext cmdCtx = PipelineCommandContext.builder()
        .runtime(ctx.getExecutorRuntime())
        .workspace(ctx.getWorkspace())
        .build();
    
    ExecResult result = pipelineCommandExecutor.exec(cmdCtx, cloneScript, logSink);
    if (result.getExitCode() != 0) {
        throw new RuntimeException("源码准备失败: " + result.getStdout());
    }
}
```

**方案B：显式Checkout step（推荐）**

用户在YAML中显式声明checkout step：

```yaml
stages:
  build:
    jobs:
      build:
        runsOn:
          group: kubernetes/build-cluster
        steps:
          checkout:
            step: Checkout  # 新增step类型
            repository: ${vars.repoUrl}
            branch: ${vars.branch}
            depth: 1  # 浅克隆
          
          build:
            step: Command
            script: mvn clean package
```

新增CheckoutStepHandler：

```java
@Component
public class CheckoutStepHandler implements PipelineStepHandler {
    
    @Override
    public boolean supports(String stepType) {
        return "Checkout".equals(stepType);
    }
    
    @Override
    public StepRuntimeRequirement runtimeRequirement() {
        return StepRuntimeRequirement.JOB_RUNTIME;
    }
    
    @Override
    public StepResult handle(PipelineExecutionContext ctx) {
        String script = buildGitCloneScript(ctx);
        // 执行git clone
        ExecResult result = pipelineCommandExecutor.exec(...);
        
        if (result.getExitCode() == 0) {
            ctx.setSourcePrepared(true);
            return StepResult.continueWith("源码检出成功");
        } else {
            return StepResult.fail("源码检出失败", result.getStdout());
        }
    }
}
```

### 4. K8s Pod镜像要求

执行git和构建操作的Pod需要包含必要工具：

#### 4.1 基础构建镜像

```dockerfile
FROM ubuntu:22.04

RUN apt-get update && apt-get install -y \
    git \
    curl \
    wget \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# 可选：安装特定语言工具链
# Java
RUN apt-get install -y openjdk-17-jdk maven

# Node.js
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs

WORKDIR /workspace
```

#### 4.2 配置默认镜像

```yaml
devops:
  config:
    build:
      cluster:
        environment-id: 1
        namespace: devops-build
        default-image: registry.example.com/devops/builder:latest  # 包含git的构建镜像
        kaniko-image: gcr.io/kaniko-project/executor:latest
```

### 5. Git凭证管理

#### 5.1 通过Secret注入

在K8s中创建包含git凭证的Secret：

```bash
kubectl create secret generic git-credentials \
  --from-literal=username=bot \
  --from-literal=password=xxx \
  -n devops-build
```

#### 5.2 Pod挂载Secret

```java
// K8sPipelineJobRuntimeManager.java
private void injectGitCredentials(Pod pod) {
    // 从环境变量或Secret中读取git凭证
    pod.getSpec().getContainers().get(0).getEnv().addAll(List.of(
        new EnvVarBuilder()
            .withName("GIT_USERNAME")
            .withValueFrom(new EnvVarSourceBuilder()
                .withSecretKeyRef(new SecretKeySelectorBuilder()
                    .withName("git-credentials")
                    .withKey("username")
                    .build())
                .build())
            .build(),
        new EnvVarBuilder()
            .withName("GIT_PASSWORD")
            .withValueFrom(new EnvVarSourceBuilder()
                .withSecretKeyRef(new SecretKeySelectorBuilder()
                    .withName("git-credentials")
                    .withKey("password")
                    .build())
                .build())
            .build()
    ));
}
```

#### 5.3 使用凭证

```bash
# 在git脚本中使用
git clone https://${GIT_USERNAME}:${GIT_PASSWORD}@gitlab.example.com/project.git
```

### 6. 平台服务瘦身

重构后，平台服务不再需要：
1. 本地git环境
2. Docker daemon
3. GitWorkspaceService的本地实现（保留接口用于K8s脚本生成）

## 实施计划

### Phase 1: 基础改造（本次）

1. ✅ 创建CheckoutStepHandler，在K8s中执行git clone
2. ✅ 修改CodeMergeStepHandler，改为JOB_RUNTIME，通过脚本执行merge
3. ✅ 更新K8sPipelineJobRuntimeManager，支持git凭证注入
4. ✅ 准备包含git的构建镜像
5. ✅ 更新pipeline YAML示例和文档

### Phase 2: 清理废弃代码（后续）

1. 标记GitWorkspaceService本地实现为@Deprecated
2. 移除PrivateRegistryDockerBuildStepHandler
3. 清理本地workspace目录管理逻辑
4. 更新测试用例

### Phase 3: 增强功能（未来）

1. Git子模块支持
2. 大仓库浅克隆优化
3. Git LFS支持
4. SSH密钥认证

## 配置示例

### application-devops-k8s.yml

```yaml
devops:
  config:
    build:
      cluster:
        environment-id: 1
        namespace: devops-build
        default-image: registry.gigimed.cn/devops/builder:latest  # 包含git的镜像
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

### Pipeline YAML示例

```yaml
variables:
  - key: repoUrl
    value: https://gitlab.gigimed.cn/gigi/gone-cloud.git
  - key: branch
    value: master

stages:
  build:
    jobs:
      checkout-and-build:
        runsOn:
          group: kubernetes/build-cluster
        steps:
          checkout:
            step: Checkout
            repository: ${vars.repoUrl}
            branch: ${vars.branch}
          
          compile:
            step: Command
            script: |
              cd yudao-server
              mvn clean package -DskipTests
          
          build-image:
            step: KanikoBuild
            dockerfile: Dockerfile
            context: ./yudao-server
            imageTag: registry.gigimed.cn/gigi/yudao:${env.BUILD_NUMBER}
  
  deploy:
    jobs:
      deploy-to-dev:
        needs: [checkout-and-build]
        steps:
          approval:
            step: Approval  # 平台执行
            approvers: [admin]
          
          deploy:
            step: K8sDeploy  # 平台执行
            environment: dev
            manifest: k8s/deployment.yaml
```

## 兼容性

### 向后兼容

1. 保留`sourceWorkspaceRequired()`接口，但实现改为在K8s中准备
2. 原有pipeline定义需要添加显式Checkout step
3. 提供迁移工具自动添加Checkout step

### 迁移指南

对于现有pipeline，添加checkout step：

**迁移前：**
```yaml
stages:
  build:
    jobs:
      build:
        steps:
          compile:
            step: Command
            script: mvn clean package
```

**迁移后：**
```yaml
stages:
  build:
    jobs:
      build:
        steps:
          checkout:  # 新增
            step: Checkout
            repository: ${vars.repoUrl}
            branch: ${vars.branch}
          
          compile:
            step: Command
            script: mvn clean package
```

## 风险和注意事项

1. **Git凭证安全**：确保Secret权限配置正确，避免泄露
2. **网络访问**：K8s集群需要能访问Git仓库
3. **存储性能**：大仓库clone可能较慢，考虑使用depth=1浅克隆
4. **Pod镜像大小**：构建镜像包含所有工具链，注意优化大小

## 总结

重构后的架构：
- ✅ 平台服务轻量化，只做编排和状态管理
- ✅ 所有环境操作委派K8s，易于扩展
- ✅ 统一使用Kaniko构建，无需Docker daemon
- ✅ 职责清晰，易于维护和测试
