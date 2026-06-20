# 重写产研一体化项目 README

## Goal

将根目录 `README.md` 从通用芋道脚手架介绍重写为当前项目的产研一体化平台说明，准确反映本仓库围绕项目、代码源、应用、变更、流水线、环境、部署与观测建设的业务定位和技术形态。

## Requirements

* 删除现有 README 中与当前项目无关的脚手架品牌、演示地址、开源营销、商城/CRM/ERP 等未启用业务模块介绍。
* 新 README 以“产研一体化平台”为主线，说明项目目标、核心闭环、当前能力、技术架构、模块结构和本地开发入口。
* README 内容必须来自现有代码、SQL、模块结构、DevOps 文档和历史 PRD，不虚构已完成能力。
* 保留对基础底座的说明：Spring Boot/Spring Cloud Alibaba、多模块 Maven、system/infra/bpm/devops/project 组合、可单体运行也可拆服务演进。
* 明确当前重点模块：`yudao-module-devops` 和 `yudao-module-project`。
* 给出关键目录、核心 SQL、常用 Maven 命令、开发约定入口。

## Acceptance Criteria

* [x] `README.md` 不再包含原芋道脚手架的大段营销和无关模块说明。
* [x] README 能让新成员快速理解“这个项目是做什么的、当前已经有哪些核心能力、从哪里开始看代码”。
* [x] README 中的功能点与现有代码/历史 PRD 可对应。
* [x] Markdown 结构清晰，无明显格式错误。

## Definition of Done

* README 完成重写。
* 运行轻量文本检查，确认没有残留明显脚手架宣传语和格式问题。
* 不需要运行 Maven 测试；本任务只改文档。

## Technical Approach

通过代码和历史 PRD 汇总当前业务边界：

* 根 `pom.xml` 当前 reactor 启用 `system`、`infra`、`bpm`、`devops`、`project`，其他脚手架模块暂时注释。
* `yudao-module-devops` 包含代码源、应用、变更、环境、主机、流水线、部署单、离线镜像包、日志与终端等后端能力。
* `yudao-module-project` 已按 `api/server` 结构搭建，用于后续项目管理能力。
* `sql/mysql/devops.sql` 定义 DevOps 核心表：代码源、应用、变更、环境、应用环境、变更环境、流水线定义/版本/运行/job/log、部署单、离线镜像包、构建主机。
* `PIPELINE_YAML_SPEC.md` 和 `PIPELINE_DOCKER_RUNTIME_DESIGN.md` 是流水线 YAML 与 Docker 临时构建环境的主要契约文档。

## Decision (ADR-lite)

**Context**: 当前 README 仍是脚手架上游介绍，无法表达本仓库正在形成的产研一体化平台，也会误导新成员关注未启用模块。

**Decision**: README 改为项目级说明文档，聚焦产研一体化业务闭环和真实启用模块；脚手架来源不作为主线，只在技术底座中简要说明。

**Consequences**: README 更贴近当前代码现状，但不是完整用户手册；详细接口和契约仍指向模块文档、SQL 和 Trellis 历史 PRD。

## Out of Scope

* 不修改 Java 代码、SQL 或构建配置。
* 不补写完整部署手册或用户操作手册。
* 不更新 `.trellis/spec/`。

## Technical Notes

* Inspected: `README.md`, root `pom.xml`, `yudao-module-devops/PIPELINE_YAML_SPEC.md`, `yudao-module-devops/PIPELINE_DOCKER_RUNTIME_DESIGN.md`, `yudao-module-devops` source tree, `yudao-module-project` source tree, `sql/mysql/devops.sql`, related `.trellis/tasks/**/prd.md`.
* Relevant active/archive PRDs include repository provider, application/repository linkage, change creation and branch creation, visual pipeline orchestration, pipeline code merge, Jenkins/SSH refactor, Docker runtime logs, K8s deployment nodes, K8s/Docker environment dashboards, pod/container logs, private registry Docker build, pipeline rollback, and project module bootstrap.
