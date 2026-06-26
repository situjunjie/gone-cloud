# 支持 K8s 多文档 Service Deployment 部署

## Goal

让流水线 `K8sDeploy` 步骤在 `RAW_MANIFEST` 模式下支持一个 `manifestYaml` 中按文档顺序部署 Service 和单个 Deployment，满足“先创建/更新 Service，再创建/更新 Deployment”的常见发布场景。

当前实现只接受单个 Deployment YAML；包含 `---` 的多文档 YAML 或 Service 文档会被拒绝。

## Requirements

- `K8sDeploy` 的 `manifestYaml` 支持 Kubernetes 多文档 YAML。
- 支持的资源范围仅限 `Service` 和 `Deployment`。
- 一个 `manifestYaml` 中必须包含且只能包含一个 `Deployment`，该 Deployment 仍作为部署单的主 workload。
- 可以包含 0 个或多个 `Service`，并按 YAML 中出现的顺序提交到集群。
- `containerName`、`image`、`replicas`、`rolloutTimeoutSeconds` 的既有语义保持不变：
  - 只作用于唯一 Deployment。
  - 部署成功仍以唯一 Deployment rollout ready 为准。
- 变量渲染能力保持不变，继续支持 `${IMAGE}`、`${APP_KEY}`、`${COMMIT_SHA}`、`${BRANCH_NAME}`、`${PIPELINE_RUN_ID}`、`${ENV_KEY}`、`${NAMESPACE}` 等现有占位符。
- 单个 Deployment YAML 的旧用法必须保持兼容。
- 节点参数字段名保持 `manifestYaml` 不变，避免影响已有流水线定义。

## Acceptance Criteria

- [x] 单个 Deployment YAML 仍可部署成功。
- [x] `Service --- Deployment` YAML 会按 Service、Deployment 的顺序 `createOrReplace`。
- [x] `Deployment --- Service` YAML 会按 Deployment、Service 的顺序提交，但最终仍等待 Deployment rollout ready。
- [x] 多文档 YAML 中缺少 Deployment 时返回明确失败。
- [x] 多文档 YAML 中包含多个 Deployment 时返回明确失败。
- [x] 多文档 YAML 中包含非 Service/Deployment 资源时返回明确失败。
- [x] 现有 `K8sImageUpgrade` 行为不受影响。
- [x] 覆盖相关单元测试。

## Notes

- 暂不支持 ConfigMap、Secret、Ingress、Job 等其他 Kubernetes 资源。
- 暂不调整 `deployment_order` 表结构；部署单仍记录唯一 Deployment 的 workload 信息。
- 暂不支持多个 Deployment 的 rollout 聚合。
