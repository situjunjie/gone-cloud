# design devops jenkins-compatible pipeline nodes

## Goal

为 DevOps 流水线设计一套 Jenkins 完全可执行的节点开发方案，使平台可视化节点与生成的 Jenkins `stage` 一一对应，并覆盖后端 jar 构建、Docker 打包/推送、制品上传、前端 npm 打包等交付场景。当前阶段先完成技术方案评审，评审通过后再进入实现。

## What I Already Know

- 用户要求先设计技术方案，确认后再开始开发。
- 现有后端已经有平台持有流水线定义、版本、DSL 校验、Jenkinsfile 生成、Jenkins Runner 触发、Jenkins 回调、节点运行日志等基础能力。
- 现有可配置节点包括 `CHECKOUT`、`UNIT_TEST`、`BUILD_ARTIFACT`、`BUILD_IMAGE`、`REPORT_ARTIFACTS`、`MOCK`，但实现仍偏命令模板，参数 schema 较弱。
- 现有 Jenkinsfile 由后端生成，通过 Runner Job 的 `JENKINSFILE_TEXT` 执行，并用 `goneDevopsCallback(...)` 回调平台。
- 当前工作区没有可用的前端源码目录；`git status` 显示 `yudao-ui/.../README.md` 已删除，这不是本次任务造成的，后续不能擅自恢复或提交。

## Decisions

- Jenkins 继续只作为执行器，流水线定义仍由平台持有，不把 DSL 所有权迁移到 Jenkins Job。
- 本次节点优先支持线性 stage 编排，不引入 parallel/matrix。
- “制品上传”本期按 Jenkins 原生 `archiveArtifacts` 实现；Nexus/MinIO 等外部制品库上传不进入本期。
- 前端后续应按后端 `paramSchema` 动态渲染节点配置，不在前端硬编码 Jenkins 参数。

## Requirements

- 平台节点必须与 Jenkins stage 一一对应。
- 节点生成的 stage 内容必须与该节点语义一致，不能只生成笼统 shell 模板。
- 平台节点必须暴露与对应 Jenkins stage/step 等价的可配置参数。
- 支持后端 Maven jar 构建。
- 支持 Docker 镜像构建，建议同时支持推送镜像仓库。
- 支持制品上传/归档。
- 支持前端 npm 构建。
- 保留现有代码合并内置节点和 Jenkins 回调链路。
- 保存草稿可保留无效 DSL，发布必须通过校验。

## Acceptance Criteria

- [ ] 技术方案明确节点类型、参数模型、Jenkinsfile 生成方式、校验规则、前端 schema 契约、测试范围和分阶段落地计划。
- [ ] 每个目标节点都有明确 Jenkins stage 示例或生成规则。
- [ ] 方案说明如何兼容现有 `CHECKOUT/BUILD_ARTIFACT/BUILD_IMAGE/REPORT_ARTIFACTS/MOCK`。
- [ ] 方案说明制品上传使用 Jenkins archive 还是外部制品库，并标记需要用户确认的差异。
- [ ] 用户评审确认后，任务才能进入实现阶段。

## Out of Scope

- 本轮不写业务代码。
- 本轮不提交 git commit。
- 本轮不实现部署 K8S、平台审批、parallel/matrix、Jenkins 多 Job 编排。
- 本轮不实现 Nexus/MinIO 等外部制品库上传。
- 本轮不修改用户已有的 `yudao-ui` 删除状态。

## Technical Notes

- Core backend files inspected:
  - `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/framework/pipeline/PipelineSpec.java`
  - `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/PipelineNodeRegistryServiceImpl.java`
  - `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/PipelineSpecValidationServiceImpl.java`
  - `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/JenkinsfileGeneratorServiceImpl.java`
  - `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/runtime/JenkinsPipelineNodeRuntimeHandler.java`
  - `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/jenkins/PipelineJenkinsCallbackServiceImpl.java`
  - `yudao-module-devops/JENKINS_RUNNER_CONFIGURATION.md`
- Relevant project guideline:
  - `.trellis/spec/backend/devops-pipeline-guidelines.md`
- Jenkins research:
  - `.trellis/tasks/06-08-devops-jenkins-pipeline-nodes/research/jenkins-pipeline-stage-capabilities.md`
