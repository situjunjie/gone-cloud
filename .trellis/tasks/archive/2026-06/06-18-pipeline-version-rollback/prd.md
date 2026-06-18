# 流水线版本回退与清理 Jenkins 旧字段

## Goal

清理流水线版本模型中残留的 Jenkins 兼容字段，并为流水线定义增加“版本回退”能力。回退不修改历史版本内容，而是基于选中的历史版本生成一个新的版本，保持版本历史、运行记录和审计链稳定可追溯。

## What I already know

* 当前版本模型在 `dev_pipeline_definition_version` 上维护草稿和发布版本，`PipelineDefinitionServiceImpl.publish` 的现有语义是“从 draft 复制生成一个新的 published 版本”。
* `PipelineDefinitionDO.publishedVersionId` 表示当前生效的已发布版本；流水线运行记录 `dev_pipeline_run.definition_version_id` 直接关联具体版本记录。
* `PipelineDefinitionVersionDO` 仍然保留了 Jenkins 遗留字段 `jenkinsfileText`、`jenkinsfileChecksum`，注释已标记为 deprecated。
* `PipelineDefinitionVersionRespVO` 和 `PipelineValidationRespVO` 也仍暴露 Jenkins 遗留字段。
* 跟踪的 MySQL 初始化脚本 `sql/mysql/devops.sql` 里，`dev_pipeline_definition_version` 仍包含 `jenkinsfile_text`、`jenkinsfile_checksum` 列。
* 当前版本状态字典包含 `DRAFT(0)`、`PUBLISHED(1)`、`ARCHIVED(2)`。
* 用户已经明确建议：回退应基于历史版本生成新版本，而不是把当前版本直接改回去；示例语义为“当前 v10，选择回退到 v6，生成内容等同于 v6 的新版本 v11，并记录 rollback_from=v6, based_on_current=v10”。

## Assumptions

* 项目尚未上线，可以接受删除 Jenkins 遗留字段，并同步调整后端接口和跟踪的 SQL 模式定义。
* 回退后需要保留完整的历史版本链，不能修改旧版本记录。
* 现有运行记录、发布记录继续通过 `definition_version_id` 关联到当时的版本，不需要回写历史数据。
* 如果现有数据表中还残留 Jenkins 列，本任务只更新仓库中的实体、接口和跟踪 SQL；真实环境建表/迁移由部署方按上线前最新结构执行。

## Open Questions

* 回退操作生成的新版本，是否应该在创建后立即作为新的“已发布版本”生效，还是先生成一个新草稿供用户再确认发布？

## Requirements

* 删除流水线版本模型中的 Jenkins 遗留字段，包括后端 DO / VO / 跟踪 SQL 中的 `jenkinsfileText`、`jenkinsfileChecksum` 及对应列。
* 审查并移除版本回路中不再需要的 Jenkins 兼容逻辑或注释，避免后续继续暴露无效字段。
* 为流水线定义增加“版本回退”接口，输入至少包含目标流水线定义、被回退到的历史版本编号，以及必要的操作上下文。
* 回退目标必须是该流水线定义下存在的历史版本，且不能直接修改该历史版本或当前已发布版本记录。
* 回退必须生成一条新的版本记录，版本号按已发布版本序列继续递增。
* 新版本的版本内容至少复制目标历史版本的 `diagramJson`、`specJson`、`nodeSchemaVersion`、`validationResultJson` 等执行相关数据。
* 新版本需要记录回退关系，至少能表达“rollback from 哪个历史版本”“基于当前哪个版本触发回退”。
* 回退生成的新版本需要保留操作人和操作时间等审计信息，并保证版本列表可查询到这些信息。
* 流水线当前生效版本的切换规则需要与现有 `publishedVersionId` 机制保持一致，不破坏运行时按版本执行的行为。
* 更新必要的测试，覆盖回退成功、目标版本不存在/不属于当前定义、版本号递增、历史版本不被修改等核心场景。

## Acceptance Criteria

* [ ] `PipelineDefinitionVersionDO`、相关响应对象和 `sql/mysql/devops.sql` 中不再出现 Jenkins 遗留字段。
* [ ] 新增回退接口后，选择历史版本回退会生成一条新的版本记录，而不是修改历史版本或当前版本。
* [ ] 新版本能正确记录回退来源和回退发生时的当前版本上下文。
* [ ] 现有按 `definition_version_id` 读取版本执行流水线的逻辑不需要回写历史数据，且仍能稳定关联旧运行记录。
* [ ] 相关单元测试/集成测试通过，或无法运行时明确说明原因。

## Definition of Done

* 后端代码、接口对象、SQL 模式定义一致更新。
* 版本回退行为与现有发布模型保持一致且可审计。
* 无关模块不做顺手重构。

## Out of Scope

* 历史运行记录的迁移或重算。
* Jenkins 运行链路的全面移除；本任务只处理流水线版本相关的遗留字段和回退能力。
* 前端完整交互改造（如果本仓库没有对应前端实现，则仅在交付说明中给出接口变更点）。

## Technical Notes

* 重点实现位置初步包括：
  * `yudao-module-devops/.../PipelineDefinitionVersionDO.java`
  * `yudao-module-devops/.../PipelineDefinitionServiceImpl.java`
  * `yudao-module-devops/.../PipelineController.java`
  * `yudao-module-devops/.../PipelineDefinitionVersionRespVO.java`
  * `yudao-module-devops/.../PipelinePublishReqVO.java` 或新增回退请求 VO
  * `sql/mysql/devops.sql`
* 当前版本列表接口 `/devops/pipeline/version/list` 已存在，可作为回退入口的数据来源。
* 现有版本号规则是：草稿固定为 `0`，发布版本从 `1` 递增。
