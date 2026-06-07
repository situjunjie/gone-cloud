# Fix DevOps Pipeline Menu Permission

## Goal

修正 DevOps 流水线设计器的菜单与权限 SQL，使其匹配前端实际页面入口：应用详情页的应用环境行点击“配置流水线”进入隐藏设计器页面，而不是在 DevOps 侧边栏展示一个独立流水线菜单。

## What I Already Know

- 当前 `sql/mysql/devops-menu.sql` 已有一轮小修：流水线菜单段会重新初始化 `@devops_menu_id`，避免单独执行时报 `parent_id cannot be null`。
- 前端新增页面：
  - 页面名称：`DevopsPipelineDesigner`
  - 路由：`/devops/pipeline/designer?applicationEnvId=xxx`
  - 前端文件：`src/views/devops/pipeline/designer.vue`
  - 入口：应用详情页“应用环境”页签，每行“配置流水线”按钮。
- 需要使用的权限点：
  - `devops:pipeline:query`：进入流水线配置、校验、预览 Jenkinsfile、查看版本历史。
  - `devops:pipeline:update`：保存草稿。
  - `devops:pipeline:publish`：发布流水线。
- 可预留但前端暂未使用：
  - `devops:pipeline:create`
  - `devops:pipeline:delete`

## Requirements

- 将流水线配置菜单配置为 DevOps 应用菜单下的隐藏菜单。
- 菜单名：`流水线配置`。
- 路由地址：`/devops/pipeline/designer`，作为 `DevOps 应用` 菜单下的隐藏菜单配置。
- 组件路径：`devops/pipeline/designer`。
- 组件名：`DevopsPipelineDesigner`。
- 权限：`devops:pipeline:query`。
- 菜单隐藏，不显示在侧边栏。
- 激活菜单指向 `/devops/application`。
- 保留按钮权限：query、update、publish。
- 可按后端完整权限模型预留 create、delete。
- 修复 SQL 变量依赖问题，避免单独执行流水线相关段落时报 `parent_id cannot be null`。

## Acceptance Criteria

- [x] `sql/mysql/devops-menu.sql` 中不再创建 DevOps 顶层下可见的 `pipeline` 独立菜单。
- [x] 流水线 designer 菜单挂在应用菜单下，且隐藏。
- [x] 流水线按钮权限挂在 designer 菜单下。
- [x] SQL 仍可幂等重复执行。
- [x] SQL 片段独立执行时不会因为 `@devops_menu_id` 或 `@devops_application_menu_id` 未初始化而插入 `NULL parent_id`。

## Out of Scope

- 不改 Java Controller 权限注解。
- 不改前端页面或路由文件。
- 不新增删除流水线接口。

## Technical Notes

- 主要文件：`sql/mysql/devops-menu.sql`。
- 相关后端权限注解已经使用：
  - `devops:pipeline:query`
  - `devops:pipeline:update`
  - `devops:pipeline:publish`
- `system_menu` 表和 `MenuDO` 当前没有 `active_menu` 字段；激活 `/devops/application` 需要前端路由 meta 配置，不能由该 SQL 写入。
