# Pipeline执行架构重构完成总结

## 重构完成情况

### ✅ 已完成的工作

#### 1. 新增Checkout步骤处理器
- **文件**: `CheckoutStepHandler.java`
- **功能**: 在K8s Pod中执行git clone
- **特性**:
  - 支持浅克隆（depth参数）
  - 支持指定commit SHA或tag
  - 支持子模块（submodules: true/recursive）
  - 支持检出到子目录
  - 自动使用K8s Secret中的git凭证
  - 可在step参数中覆盖凭证

#### 2. 注册新步骤类型
- **Checkout**: 代码检出步骤
- **KanikoBuild**: Kaniko镜像构建步骤（已有handler，新增注册）

#### 3. CodeMerge改为K8s执行
- 修改`CodeMergeStepHandler.runtimeRequirement()`返回`JOB_RUNTIME`
- 添加`sourceWorkspaceRequired()`返回`true`
- 现在CodeMerge在K8s Pod中执行，不再在平台本地执行

#### 4. K8s Git凭证注入
- 修改`K8sPipelineJobRuntimeManager.buildPod()`
- 添加`injectGitCredentialsFromSecret()`方法
- 自动从K8s Secret `git-credentials`注入环境变量：
  - `GIT_USERNAME`
  - `GIT_PASSWORD`
  - `GIT_USER_NAME`
  - `GIT_USER_EMAIL`

#### 5. 构建镜像和部署文档
- **builder.Dockerfile**: 包含git、Java、Node.js、Docker CLI、kubectl的构建镜像
- **build-builder-image.sh**: 构建和推送镜像的脚本
- **k8s-git-credentials-secret.yaml**: Git凭证Secret配置示例
- **k8s-build-example-pipeline.yaml**: 完整的测试Pipeline示例
- **DEPLOYMENT_GUIDE.md**: 快速部署指南

## 架构变化对比

### 重构前
```
Platform Service:
├─ Git操作 (本地执行) ❌
├─ Docker构建 (本地daemon) ❌
├─ 命令执行 (本地/Docker) ⚠️
└─ 审批 (平台) ✅
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

## Pipeline YAML示例

### 完整示例
```yaml
sources:
  gone_cloud_repo:
    type: gitlab
    name: Gone Cloud Repository
    endpoint: https://gitlab.gigimed.cn/gigi/gone-cloud.git
    branch: master-gigi

stages:
  build_stage:
    name: 构建阶段
    jobs:
      checkout_and_build_job:
        name: 检出代码并编译
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

          compile_step:
            name: 编译项目
            step: Command
            with:
              run: |
                cd yudao-server
                mvn clean package -DskipTests

      build_image_job:
        name: 构建镜像
        needs:
          - checkout_and_build_job
        runsOn:
          group: kubernetes/build-cluster
        steps:
          kaniko_build_step:
            name: Kaniko构建
            step: KanikoBuild
            with:
              dockerfile: yudao-server/Dockerfile
              context: ./yudao-server
              imageTag: registry.gigimed.cn/gigi/yudao:${PIPELINE_RUN_ID}

  deploy_stage:
    name: 部署阶段
    jobs:
      approval_job:
        name: 部署审批
        steps:
          approval_step:
            name: 审批
            step: Approval
            with:
              processDefinitionKey: pipeline-deploy-approval

      deploy_to_dev_job:
        name: 部署到开发环境
        needs:
          - approval_job
        steps:
          deploy_step:
            name: K8s部署
            step: K8sDeploy
            with:
              deployMode: RAW_MANIFEST
              manifestYaml: |
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: yudao-server
```

## 部署步骤

### 1. 构建Pipeline构建镜像
```bash
cd /Users/situjunjie/projects/gone-cloud/.devops
./build-builder-image.sh
```

### 2. 配置Git凭证
```bash
kubectl create secret generic git-credentials \
  --from-literal=username='your-git-username' \
  --from-literal=password='your-git-token' \
  --from-literal=user-name='Pipeline Bot' \
  --from-literal=user-email='bot@pipeline.local' \
  -n devops-build
```

### 3. 更新应用配置
```yaml
# application-devops-k8s.yml
devops:
  config:
    build:
      cluster:
        environment-id: 1
        namespace: devops-build
        default-image: registry.gigimed.cn/devops/builder:latest
        kaniko-image: gcr.io/kaniko-project/executor:latest
        workspace-storage:
          type: SHARED_PVC
          shared-pvc-name: devops-workspace
```

### 4. 测试Pipeline
使用 `.devops/k8s-build-example-pipeline.yaml` 创建测试pipeline。

## 文件清单

### 新增/修改的Java文件
1. ✅ `CheckoutStepHandler.java` - 新增
2. ✅ `PipelineNodeRegistryServiceImpl.java` - 修改（注册Checkout和KanikoBuild）
3. ✅ `KanikoDockerBuildStepHandler.java` - 修改（使用TYPE_KANIKO_BUILD常量）
4. ✅ `CodeMergeStepHandler.java` - 修改（改为JOB_RUNTIME）
5. ✅ `K8sPipelineJobRuntimeManager.java` - 修改（注入git凭证）

### 新增的部署文件
1. ✅ `.devops/builder.Dockerfile` - 构建镜像定义
2. ✅ `.devops/build-builder-image.sh` - 构建脚本
3. ✅ `.devops/k8s-git-credentials-secret.yaml` - Secret配置
4. ✅ `.devops/k8s-build-example-pipeline.yaml` - Pipeline示例
5. ✅ `.devops/DEPLOYMENT_GUIDE.md` - 部署指南

### 文档
1. ✅ `REFACTOR_PLAN.md` - 完整重构方案
2. ✅ `.devops/DEPLOYMENT_GUIDE.md` - 快速部署指南

## 下一步行动

### 立即可做
1. **构建镜像**: 执行 `build-builder-image.sh`
2. **创建Secret**: 在K8s中创建git-credentials
3. **测试Pipeline**: 使用示例YAML创建测试pipeline

### 后续优化
1. **迁移现有Pipeline**: 将现有pipeline更新为新格式
2. **优化构建镜像**: 根据实际需求定制工具链
3. **配置Maven缓存**: 挂载.m2目录到PVC
4. **添加构建监控**: 接入Prometheus监控Pod资源

### 待废弃
1. `PrivateRegistryDockerBuildStepHandler` - 使用Kaniko替代
2. 本地git操作相关代码 - 已迁移到K8s

## 验证清单

部署后验证项：
- [ ] 编译成功（已完成✅）
- [ ] 构建镜像已推送到仓库
- [ ] K8s Secret git-credentials已创建
- [ ] 应用配置更新
- [ ] 创建测试pipeline
- [ ] Checkout step成功
- [ ] Command step成功
- [ ] KanikoBuild成功
- [ ] 查看Pod日志
- [ ] 验证workspace共享

## 技术亮点

1. **职责清晰**: 平台只做编排，环境操作全部委派K8s
2. **安全可控**: Git凭证通过K8s Secret管理，不暴露在代码中
3. **易于扩展**: 新增step只需实现handler，无需修改底层
4. **向后兼容**: 保留旧step类型的支持
5. **灵活配置**: 支持per-step的凭证覆盖

## 常见问题参考

详见 `.devops/DEPLOYMENT_GUIDE.md` 的"常见问题"章节，包括：
- Git克隆失败
- 镜像拉取失败
- 工作区不共享
- Kaniko构建失败

## 总结

✅ 重构成功完成，所有代码编译通过
✅ 架构清晰，职责分离
✅ 文档完整，可立即部署测试
✅ 向后兼容，平滑迁移

**下一步**: 构建镜像并在K8s环境中测试完整流程。
