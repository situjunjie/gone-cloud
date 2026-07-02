# Implementation Plan: DevOps 审批携带发布详情

## Checklist

- [x] Phase 2 前加载 backend 相关 Trellis specs，重点是 pipeline、quality、error-handling、logging。
- [x] 新增 `PipelineApprovalDeployDetailBuilder`，封装发布详情查询和 HTML 组装。
- [x] 在 `PipelineApprovalServiceImpl.buildVariables(...)` 中加入 `deploy_html`。
- [x] 为 `PipelineApprovalServiceImplTest` 增加 `deploy_html` 变量断言，并补齐新增依赖 mock。
- [x] 新增 `PipelineApprovalDeployDetailBuilderTest` 覆盖多变更、空变更、人员展示和 HTML 转义。
- [x] 运行 focused Maven 测试。
- [x] 如果 focused 测试暴露编译或依赖问题，修复后重跑。

## Implementation Details

1. 新增 builder 类

   路径：

   `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/approval/PipelineApprovalDeployDetailBuilder.java`

   依赖：

   - `ApplicationMapper`
   - `ApplicationEnvMapper`
   - `EnvironmentMapper`
   - `ChangeMapper`
   - `AdminUserApi`

   方法：

   - `String buildDeployHtml(PipelineRunDO run)`

2. 变更解析

   - `JsonUtils.parseArray(run.getChangeSnapshotJson(), PipelineRunChangeSnapshotContext.class)`
   - 快照为空时回退 `run.getChangeId()`
   - 使用 `LinkedHashMap` 保存 `changeId -> snapshotCommitSha`，保证输出顺序

3. 人员解析

   - 收集非空 `testerUserId` 和 `codeReviewerUserId`
   - 空集合不调用用户 RPC
   - 批量调用 `AdminUserApi.getUserMap(ids)`
   - 捕获用户 RPC 查询异常，记录 warn，继续展示 ID

4. HTML 组装

   - 所有动态值通过 `HtmlUtils.htmlEscape(...)`
   - 空值使用 `-`
   - commit SHA 可按原值展示，不在本任务中截断，避免信息损失
   - 不生成链接

5. 审批变量接入

   - `PipelineApprovalServiceImpl` 注入 builder
   - `buildVariables(...)` 最后加入：

   ```java
   variables.put("deploy_html", deployDetailBuilder.buildDeployHtml(run));
   ```

   - 其余变量名保持不变

## Validation Commands

Focused tests:

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -Dtest='PipelineApprovalServiceImplTest,PipelineApprovalDeployDetailBuilderTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

If compile scope failures suggest dependent modules need building:

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile
```

## Risk Points

- `PipelineApprovalContext.variables` 会保存 `deploy_html`，因此 HTML 不能包含敏感信息。
- `AdminUserApi` 远程调用失败不应阻断审批发起；只做显示降级。
- 多变更输出必须按本次发布快照顺序，不按数据库返回顺序。
- HTML 转义不能遗漏提交信息、标题、分支名这类外部输入。

## Rollback

如果上线后 BPM 表单展示异常，可回滚本次 DevOps 代码改动；BPM API 和流程配置未变，不需要数据库回滚。
