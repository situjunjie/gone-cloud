# K8sDeploy 引用应用编排配置

## Goal

流水线部署直接引用应用编排保存的 Kubernetes YAML，避免重复维护 manifestYaml。

## Background

用户确认方案 A：复用 K8sDeploy / K8sDeployStepHandler，新增 ORCHESTRATION 模式；确认支持完整六类资源及恰好一个启用的 Deployment，并明确要求开始实现。旧编排配置阶段不涉及部署，参见 `.trellis/tasks/archive/2026-08/06-30-application-orchestration-k8s-design/design.md`。

## Requirements

- R1. 增加 ORCHESTRATION，RAW_MANIFEST 仍只支持 Service + 唯一 Deployment，K8sImageUpgrade 行为不变。
- R2. 首次创建部署单时读取 run.appId 对应的 K8S_DEPLOYMENT 草稿；缺失、空或无启用组件时失败，不回退到步骤 YAML。
- R3. 仅提交 enabled=true 的组件，按 applyOrder 升序执行，支持 Secret、ConfigMap、PersistentVolumeClaim、Service、Deployment、Ingress；必须恰好一个启用的 Deployment。不支持的类型、非法组件和依赖禁用组件在提交集群前失败。
- R4. 编排模式无需 manifestYaml；containerName 和 image 仍必填，replicas / rolloutTimeoutSeconds 沿用现有语义；沿用固定部署变量替换，不增加变量组语法。
- R5. 保存原始及渲染 YAML、来源编排标识及更新时间；同一部署单重试复用快照，新流水线部署读取最新草稿。
- R6. 复用部署单、逐资源结果、取消、重试与流水线状态汇总；唯一 Deployment 为健康门禁，其余资源仅创建或更新，不检查 Service Endpoints、PVC Bound 或 Ingress 连通性。
- R7. 全部资源限定到目标环境 namespace；Secret data/stringData 不出现在流水线 context、部署详情及解析/apply 错误中。内部部署快照保存执行内容以支持重试，遵循现有租户与权限边界。

## Acceptance Criteria

- AC1 → R1：RAW_MANIFEST 与镜像升级回归通过；注册表及 DSL 校验支持新增模式。
- AC2 → R2/R3：六类资源按顺序提交，禁用组件不提交；缺失、空、非法类型、零/多 Deployment、禁用依赖失败且无集群写入。
- AC3 → R4：无 manifestYaml 的 ORCHESTRATION 配置通过校验；缺少 image/containerName 失败；镜像、副本数、命名空间覆盖正确。
- AC4 → R5：草稿更新后重试不重新读取，来源元数据与执行 YAML 保留。
- AC5 → R6：资源 apply 失败中止后续提交，保存已有结果；Deployment 健康决定成功，其他资源不参与门禁；取消和重试回归通过。
- AC6 → R7：响应、日志 context 和错误不含 Secret 内容，内部快照可用于重试。

## Out of Scope

多 Deployment、编排版本发布/历史管理、自动回滚、删除草稿中移除的集群资源、主机部署、镜像构建扩展、变量组新语法、前端仓库代码。后端注册表及 DSL 文档提供前端接入契约。
