# DevOps Pipeline Cache Path Workspace-Relative Support

## Goal

让流水线缓存目录(`cacheConfig.directories[].path`)支持相对于 run workspace 的路径,
通过 `${WORKSPACE}` 之类的变量在缓存配置里引用工作目录,从而缓存诸如
`${WORKSPACE}/node_modules`、`${WORKSPACE}/.next/cache` 这类位于源码目录内的依赖/构建缓存,
让它们跨流水线运行复用。

## Confirmed Facts(已确认的现状)

调查于 `yudao-module-devops`,结论:**当前缓存路径不能配置相对于 workspace 的相对路径**,
原因如下:

- `path` 字段语义是"容器内缓存挂载点",宿主机目录由平台按
  `sha256(containerPath)` 哈希派生,不接受宿主机路径。
  见 `PipelineCacheConfig.Directory.path`
  (`framework/pipeline/runtime/PipelineCacheConfig.java:36`)。
- 缓存路径校验 `PipelineCacheConfigResolver.validatePath`
  (`framework/pipeline/runtime/PipelineCacheConfigResolver.java:167-185`)强制:
  - 必须以 `/` 开头(绝对路径),否则报 `PIPELINE_CACHE_CONFIG_INVALID`("缓存目录必须是绝对路径")。
  - 禁止任何 `/../`、`/./`、`/..`、`/.` 相对片段(行 177-179)。
  - `FORBIDDEN_PREFIXES` 显式禁止 `/workspace`、`/proc`、`/sys`、`/dev`、`/run`、`/tmp`
    前缀(行 41、180-184)。所以 `/workspace/node_modules` 这类路径当前直接被拒绝。
- 缓存挂载在 `LocalPipelineWorkspaceService.createWorkspace`
  (`framework/pipeline/runtime/LocalPipelineWorkspaceService.java:50-57`)构建,
  宿主机路径来自 `resolveCachePath` = `{cacheWorkspace}/by-path/{sha256(containerPath)}`
  (行 171-173),与 run workspace 是两个独立目录。
- Docker 挂载在 `DockerPipelineJobRuntimeManager`
  (`framework/pipeline/runtime/DockerPipelineJobRuntimeManager.java:54-58`):
  run workspace 挂到 `/workspace`(常量 `CONTAINER_WORKSPACE`,行 31),
  每个 enabled 缓存目录单独 bind 到 `cacheMount.getContainerPath()`。
  这解释了为什么 `/workspace` 前缀被禁:缓存目录若落在 `/workspace` 下会与 run workspace
  挂载嵌套冲突,且缓存目录刻意与 run workspace 分离以跨运行常驻。
- 缓存配置随版本保存,执行时使用版本快照,不经过步骤级变量替换。
  见 `PIPELINE_YAML_SPEC.md:15`、`PIPELINE_DOCKER_RUNTIME_DESIGN.md:708-751`。
- 已存在一个 `${VAR}` 占位符替换器 `PipelineVariableResolver`
  (`service/pipeline/execution/PipelineVariableResolver.java`),正则
  `\$\{([A-Za-z_][A-Za-z0-9_]*)}`(行 19),变量来自
  `PipelineStepContext`(run/job/step 字段,行 69-100)。**但它只作用于步骤的 `with:`
  参数,在步骤执行时解析,不作用于 `cacheConfig`,且当前变量集合里没有 `WORKSPACE`。**
- 前端缓存配置表单不在本仓库(前端为独立仓库,本仓库仅有构建产物)。本任务定义后端契约。

## 设计张力(需在 design 阶段定夺)

run workspace(`/workspace`)是跨运行复用的稳定目录(definition-scoped 时,
见 `PIPELINE_DOCKER_RUNTIME_DESIGN.md:708`),所以放在 `/workspace/node_modules`
的内容本身已经会保留。但目前缺少一个"显式声明 workspace 内子路径为缓存"的能力:
语义不清晰、没有独立清理入口、与"缓存目录哈希派生宿主机路径"模型不一致。

候选方向(design.md 中评估并选一):

1. **变量替换 + 放宽 `/workspace` 前缀**:允许 `path` 写 `${WORKSPACE}/node_modules`,
   在缓存解析阶段把 `${WORKSPACE}` 解析为 `/workspace`,作为容器内绝对路径正常 bind。
   需要处理 run workspace 与缓存目录的嵌套挂载语义(Docker 允许嵌套 bind,内层优先),
   并为 workspace 相对缓存单独派生宿主机持久目录。
2. **新增独立字段**(如 `workspaceRelative: true` + 相对 `path`),不复用 `/workspace`
   前缀,显式区分"容器绝对路径缓存"与"workspace 相对缓存"。
3. 维持现状,仅在文档中说明 workspace 已天然跨运行复用,不引入新配置。

用户倾向方向 1(用 `${Var}` 变量表达相对 workspace 的路径)。

## Requirements

- 在缓存配置里支持用变量引用 workspace 根,推荐 `${WORKSPACE}`(具体变量名 design 阶段定)。
- 变量在缓存解析阶段(workspace 创建时)被解析为容器内 `/workspace`,并正确派生出
  跨运行常驻的宿主机缓存目录。
- 保留原有绝对路径缓存目录的行为与默认配置(Maven/npm/gradle 等)不变,向后兼容。
- 校验规则相应调整:在保留对 `/proc`、`/sys`、`/dev`、`/run`、`/tmp` 等危险前缀防护、
  以及 `/../` 路径穿越防护的前提下,放开对 workspace 相对缓存的支持。
- 更新 `PIPELINE_YAML_SPEC.md` 与 `PIPELINE_DOCKER_RUNTIME_DESIGN.md` 的缓存章节。
- 提供后端契约说明供前端缓存配置表单对接(变量名、示例、校验错误码)。

## Acceptance Criteria

- [ ] 缓存配置项 `path` 可写 `${WORKSPACE}/<subdir>`(或最终选定的变量语法)并校验通过。
- [ ] 配置了 workspace 相对缓存的流水线,二次运行能复用上次缓存内容(跨运行持久验证)。
- [ ] 原有绝对路径缓存目录(默认配置)行为不变,既有版本无需迁移即可继续工作。
- [ ] 危险前缀与路径穿越仍被拒绝并返回 `PIPELINE_CACHE_CONFIG_INVALID`。
- [ ] 文档(YAML spec + runtime design)更新,含变量语义与示例。
- [ ] 后端单测覆盖:变量解析、宿主机路径派生、非法变量/前缀拒绝。

## Notes

- 关键文件:
  - `framework/pipeline/runtime/PipelineCacheConfigResolver.java`(校验/规范化/默认配置)
  - `framework/pipeline/runtime/LocalPipelineWorkspaceService.java`(宿主机路径派生与挂载准备)
  - `framework/pipeline/runtime/DockerPipelineJobRuntimeManager.java`(bind 挂载)
  - `framework/pipeline/runtime/PipelineCacheConfig.java` / `PipelineCacheMount.java`(结构)
  - `service/pipeline/execution/PipelineVariableResolver.java`(已有 `${VAR}` 机制可参考/复用)
- 这是 complex 任务:建议补 `design.md`(选定方向、嵌套挂载语义、宿主机派生策略)
  再进入实现。
