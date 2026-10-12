# 技术设计

## Boundary

行为差异在 DeploymentOrderServiceImpl 的配置构造及 KubernetesDeploymentManifestSupport 的资源白名单。保留 K8sDeployStepHandler 的 PLATFORM 运行时和部署单委托，新增模式不是新增 handler。

## Contract

```yaml
step: K8sDeploy
with:
  deployMode: ORCHESTRATION
  containerName: app
  image: registry.example.com/demo:${COMMIT_SHA}
  rolloutTimeoutSeconds: 300
```

默认仍为 RAW_MANIFEST。注册表不将 manifestYaml 设为所有模式必填，服务校验在 RAW_MANIFEST 下要求该字段。

## Data Flow

首次创建部署单 → ApplicationOrchestrationService.getOrchestration(run.appId, K8S_DEPLOYMENT) → 检查组件/顺序/启用依赖及每个 YAML 单资源 → 拼接有序 YAML → 固定变量渲染 → 模式白名单解析 → 验证唯一 Deployment/容器/资源身份 → 保存 config_json → 复用逐资源提交及 Deployment rollout。

编排配置服务的应用存在性校验只依赖 ApplicationMapper，不注入具有流水线启动依赖的 ApplicationService。ApplicationService → PipelineExecutionAsyncService → PipelineExecutionEngine → StepHandlerRegistry → K8sDeployStepHandler → DeploymentOrderService → ApplicationOrchestrationService 是单向链路，编排服务不能反向依赖应用发布服务。

ContainerDeployConfigContext 增加 orchestrationId / orchestrationUpdateTime。原始和渲染 YAML 沿用已有字段，不改数据库表。重试直接使用 config_json。读取失败不兜底 RAW_MANIFEST。

资源解析使用模式参数重载，旧单参数方法保留 Service/Deployment 白名单。ORCHESTRATION 开放六类，准备时逐个校验 metadata.name 并覆盖 namespace，Deployment 才覆盖镜像/副本。ORCHESTRATION 跳过 Service selector/Endpoints 健康约束，状态收集路径同样避免从 applyResults 恢复 Service 门禁。

## Secrets / Errors

原始 Secret 存在内部执行快照中，仅用于部署及重试；流水线 context 与部署详情使用拷贝后的 YAML，移除 Secret data/stringData 内容，并移除可能携带内容的 annotations。编排解析错误不回显 parser 原始消息；apply 失败仅记录资源身份和简短失败说明，避免集群错误回显 Secret payload。

## Compatibility

RAW_MANIFEST 的字段、资源白名单、Service 健康约束及默认模式不变；旧 config_json 缺少来源字段仍可解析。K8sImageUpgrade 不进入编排分支。不新增外部接口/权限，不更改草稿保存格式与画布。

## Limitations

createOrReplace 非事务操作，后续资源失败时已提交资源保留；不自动删除或回滚资源。Secret 内部快照沿用部署单租户存储，展示必须脱敏。健康门禁仍受现有 Deployment 状态采集限制。
