# 实施计划：工作项动态与发表评论

## 1. 实现步骤

1. 新增动态类型枚举、分页 Request VO、统一 Response VO 和前缀深度校验注解。
2. 为 system 操作日志 RPC 增加默认关闭的按 `createTime DESC,id DESC` 排序开关；工作项统一动态启用，既有调用保持 `id DESC`。
3. 新增 `WorkItemActivityService`：复用评论分页与工作项操作日志映射，分别获取时间线前缀，批量补齐操作人头像、稳定排序、全局截页并合计 total。
4. 抽取评论创建基础 VO，新增工作项评论请求 VO；在 `ProjectWorkItemController` 增加查询动态与发表评论端点。
5. 更新工作项前端接口文档，描述统一类型载荷、排序、分页深度、权限与最终一致性。

## 2. 测试清单

- 聚合服务：混合排序、同时间兜底、跨页、单来源为空、双来源为空、超出总量、total 合计、操作人资料补齐、删除评论及未知日志 schema 透传。
- System 操作日志：默认仍按 `id DESC`；显式打开时间线排序后按 `createTime DESC,id DESC`。
- Controller 方法安全：动态查询系统权限短路、项目权限拒绝、双层通过；发表评论仅校验 `COMMENT_CREATE` 且不调用系统权限。
- Controller 映射：固定 `WORK_ITEM + workItemId`，正文、回复、@ 用户无损传递并返回评论 ID。
- Validation：缺少 workItemId、空正文、超长正文、`pageNo * pageSize > 200` 拒绝；合法边界通过。
- 回归：`CommentServiceImplTest`、`CommentMethodSecurityTest`、`WorkItemChangeLogServiceImplTest`、`ProjectMethodSecurityTest`。

## 3. 验证命令

```bash
mvn -pl yudao-module-project/yudao-module-project-server -am \
  -Dtest=WorkItemActivityServiceImplTest,WorkItemActivityPageReqVOTest,ProjectMethodSecurityTest,CommentServiceImplTest,CommentMethodSecurityTest,WorkItemChangeLogServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl yudao-module-project/yudao-module-project-server -am test
git diff --check
```

## 4. 风险与检查点

- 风险文件：`ProjectWorkItemController`、评论创建 VO、`CommentServiceImpl`、新增活动聚合 Service。
- 不修改已有用户改动的工作项变更日志语义；聚合层只调用其公开接口。
- 若完整模块测试仍遇到已知的外部增量编译器/MapStruct 残留问题，必须区分聚焦测试结果并记录具体失败，不能将其归因于本任务。
- 激活任务前重新执行 PRD convergence pass，并通过 `trellis-before-dev` 确认 backend、project RBAC、评论、工作项变更日志、跨层与复用规范。
