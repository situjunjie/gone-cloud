# Design — Pipeline Cache Path Workspace-Relative Support

## Decision

采用 PRD 中的**方向 1**:在缓存目录 `path` 中支持 `${WORKSPACE}` 变量,语法沿用既有
`${VAR}` 约定(`PipelineVariableResolver` 的 `\$\{NAME}` 形式)。`${WORKSPACE}` 在缓存解析
阶段被解析为容器内 run workspace 挂载点(当前 `/workspace`)。其余绝对路径缓存目录行为完全不变。

不选方向 2(新增 `workspaceRelative` 布尔字段):变量语法与平台其它字段一致、对前端更直观、
未来可扩展更多变量(如 `${HOME}`)。不选方向 3(纯文档):用户明确需要可配置能力。

## 核心问题:`path` 的双重身份必须拆开

现状中 `PipelineCacheConfig.Directory.path` 同时承担两个角色:

1. **容器内挂载点**(Docker bind 的 target)——`DockerPipelineJobRuntimeManager.java:58`。
2. **宿主机目录的哈希键**——`resolveCachePath` = `{cacheWorkspace}/by-path/{sha256(path)}`
   (`LocalPipelineWorkspaceService.java:171-173`),且缓存清理
   (`clearDefinitionCache`,`:86-102`)用同一字符串重新哈希定位宿主机目录。

引入变量后这两个角色发生分叉:

- 容器挂载点需要**解析后**的形式:`/workspace/node_modules`。
- 哈希键 / 清理键 / 元数据记录需要一个**稳定且一致**的规范形式。

设计取舍:**统一用规范化后的存储字符串(canonical path,即保留 `${WORKSPACE}` 字面量的形式)
作为哈希键、元数据键、清理键;只有 Docker bind 与容器 workingDir 使用解析后的容器路径。**

理由:
- 对普通绝对路径(如 `/root/.m2`),canonical == 解析结果,哈希字符串不变,**既有缓存目录与
  元数据零迁移**。
- 对变量路径,哈希键是 `${WORKSPACE}/node_modules` 字面量,与"容器内实际挂载点"解耦。
  这样即使将来容器 workspace 挂载点从 `/workspace` 迁到 `/workspace/source`
  (设计文档 `PIPELINE_DOCKER_RUNTIME_DESIGN.md:746` 提到的可能演进),宿主机缓存身份不变,
  缓存不会因挂载点改名而失效。

## 数据结构变更

### `PipelineCacheMount`(新增解析后字段)

```
id
canonicalPath   // 规范化的存储路径,可能含 ${WORKSPACE};用于元数据记录与宿主机哈希
containerPath   // 解析后的容器内挂载点,如 /workspace/node_modules;用于 Docker bind
hostPath
description
```

`PipelineCacheConfig.Directory` 结构不变(`path` 仍是用户输入/存储的规范形式)。

### 容器 workspace 挂载点常量

把 `DockerPipelineJobRuntimeManager.CONTAINER_WORKSPACE = "/workspace"`(`:31`)抽到一个中立
常量(如新增 `PipelineWorkspaceConstants.CONTAINER_WORKSPACE`),供运行时管理器与缓存解析器
共用,避免 `PipelineCacheConfigResolver` 反向依赖 runtime 管理器。

## 变量解析

在 `PipelineCacheConfigResolver` 内新增轻量解析(不复用步骤级 `PipelineVariableResolver`,
二者阶段与变量域不同),已知变量表初期仅:

```
${WORKSPACE} -> PipelineWorkspaceConstants.CONTAINER_WORKSPACE  // "/workspace"
```

- 仅识别 `\$\{NAME}` 语法;`NAME` 命中已知表才替换,未知变量直接判非法
  (`PIPELINE_CACHE_CONFIG_INVALID`,提示"未知缓存变量")。
- `${WORKSPACE}` 只允许出现在路径**开头**;其后必须跟一个非空子路径 `/<sub>`。

## 校验规则调整(`validatePath` / `normalizeDirectory`)

规范化与校验顺序:

1. `normalizePath`:trim、合并多余 `/`、去尾部 `/`(保持现状,`:155-165`)。规范化在
   **变量字面量保留**的前提下进行(不展开变量)。
2. 判定是否变量形式(以 `${WORKSPACE}` 开头):
   - **变量形式**:
     - 解析得到 `containerPath = "/workspace" + remainder`。
     - `remainder` 必须非空(拒绝 `${WORKSPACE}` 单独使用 → "缓存目录不能是整个工作目录")。
     - 对 canonical 与 resolved 同时做路径穿越检查:拒绝 `/../`、`/./`、`/..`、`/.`
       (复用现有正则,`:177-179`),从而拦截 `${WORKSPACE}/../etc`。
     - 跳过 `/workspace` 的 `FORBIDDEN_PREFIXES` 拒绝(这是本任务的核心放开点),但
       resolved 路径仍需通过其余危险前缀检查(`/proc`、`/sys`、`/dev`、`/run`、`/tmp`)。
   - **普通绝对路径形式**:维持现状全部规则不变,**继续拒绝 `/workspace` 前缀**
     (避免用户绕过变量直接写 `/workspace/...` 造成歧义,统一引导用变量表达)。
3. 长度限制 `MAX_PATH_LENGTH` 作用于 canonical(存储字符串)。
4. **嵌套与去重检查作用于解析后的容器路径**:`rejectNestedEnabledPaths` 与重复检查改用
   resolved containerPath 比较,防止 `${WORKSPACE}/a` 与 `${WORKSPACE}/a/b`、或
   `${WORKSPACE}/x` 与一个恰好解析相同的项冲突。`directoryMap` 仍可用 canonical 作键,
   额外维护一个 resolved 集合做嵌套判断。

## 宿主机路径派生与挂载

`LocalPipelineWorkspaceService.createWorkspace`(`:50-57`)构建 mount:

```
canonicalPath = directory.getPath()                          // 规范形式
containerPath = resolver.resolveContainerPath(canonicalPath) // 解析 ${WORKSPACE}
hostPath      = resolveCachePath(cacheWorkspace, canonicalPath) // sha256(canonical)
```

- `resolveCachePath` 的哈希入参由 containerPath 改为 **canonicalPath**(对普通路径无变化)。
- `recordCachePaths`(`:175-191`)记录 **canonicalPath**(对普通路径无变化,变量路径记录字面量)。
- Docker bind(`DockerPipelineJobRuntimeManager.java:58`)用 `hostPath -> containerPath`(解析后)。

### 嵌套 bind 语义

run workspace 挂在 `/workspace`,工作区相对缓存挂在 `/workspace/<sub>`,形成嵌套 bind。
Docker daemon 按挂载点深度排序、父先子后挂载,内层 bind 遮蔽 run workspace 该子树。
docker-java 传入重叠 `Bind` 列表由 daemon 处理,无需手动排序。效果:

```
/workspace                 -> {runWorkspace}            (run 级)
/workspace/node_modules    -> {cacheWorkspace}/by-path/{sha256("${WORKSPACE}/node_modules")} (常驻)
```

## 持久化作用域(沿用现状)

`cacheWorkspace` 在 definition-scoped run 下为 `definitionRoot/cache`(跨运行常驻);standalone
run 下为 `run-{id}-cache`(单次运行)。工作区相对缓存遵循同一作用域,无新增分支。这意味着:

- 应用环境流水线(definition-scoped):`${WORKSPACE}/node_modules` 跨运行复用,满足核心诉求。
- standalone run:与其它缓存一样仅单次有效。

## 缓存清理一致性

`PipelineDefinitionServiceImpl.clearCache`(`:212-225`)与 `resolveClearableCachePaths`
(`:331-355`)从版本 `cacheConfig`(canonical)与已记录元数据(canonical)汇总可清理路径,
`clearDefinitionCache` 用同一 canonical 重新哈希定位宿主机目录。因哈希键统一为 canonical,
变量路径天然可被清理。`clearDefinitionCache` 形参语义由"containerPaths"变为 canonical 路径,
建议改名 `cachePaths`(仅命名,行为不变)。

## 文档与契约

- `PIPELINE_YAML_SPEC.md`:cacheConfig 章节补充 `${WORKSPACE}` 变量、示例
  (`{ "id": "node-modules", "path": "${WORKSPACE}/node_modules", "enabled": true }`)、
  校验错误码 `PIPELINE_CACHE_CONFIG_INVALID`。
- `PIPELINE_DOCKER_RUNTIME_DESIGN.md`:缓存章节补充工作区相对缓存的嵌套挂载示意与持久化作用域。
- 前端契约:`path` 可为绝对容器路径或 `${WORKSPACE}/<subdir>`;变量名大小写敏感;
  整个工作区(`${WORKSPACE}` 单独)与路径穿越被拒。
- `schemaVersion` 由 `1.0` 提升至 `1.1`(能力标记);缺省/旧值按兼容处理,不强制迁移。

## 重要行为差异(需在文档与前端提示中说明)

工作区相对缓存位于 `/workspace` 子树内,因此流水线中对工作区的破坏性操作
(如 `git clean -fdx`、手动 `rm -rf node_modules`)会真正删除缓存内容——这与 `/root/.m2`
等树外缓存不同。属于用户脚本可控行为,文档需明确提示,避免误以为缓存"必然保留"。

## 影响文件清单

- `framework/pipeline/runtime/PipelineCacheConfigResolver.java`:变量解析、校验放开、
  哈希键改 canonical、嵌套/去重改 resolved。
- `framework/pipeline/runtime/PipelineCacheMount.java`:新增 `canonicalPath`。
- `framework/pipeline/runtime/LocalPipelineWorkspaceService.java`:mount 构建与哈希入参、
  元数据记录改 canonical;`resolveCachePath` 入参语义。
- `framework/pipeline/runtime/DockerPipelineJobRuntimeManager.java`:bind 用 resolved
  containerPath;抽 `/workspace` 常量。
- 新增 `framework/pipeline/runtime/PipelineWorkspaceConstants.java`(或就近常量)。
- `service/pipeline/PipelineDefinitionServiceImpl.java`:`clearDefinitionCache` 形参命名
  (可选)。
- `PIPELINE_YAML_SPEC.md`、`PIPELINE_DOCKER_RUNTIME_DESIGN.md`。

## 测试计划

后端单测(`PipelineCacheConfigResolver` / `LocalPipelineWorkspaceService`):

- 合法 `${WORKSPACE}/node_modules`:规范化保留字面量、`resolveContainerPath` 得
  `/workspace/node_modules`、hostPath 由 canonical 哈希派生。
- 拒绝:`${WORKSPACE}`(整目录)、`${WORKSPACE}/../etc`(穿越)、`${FOO}/x`(未知变量)、
  `${WORKSPACE}` 解析后命中其余危险前缀的构造。
- 普通绝对路径(默认配置)行为零变化;`/workspace/x` 直写仍被拒。
- 嵌套:`${WORKSPACE}/a` 与 `${WORKSPACE}/a/b` 报嵌套错误。
- 默认配置、`resolveVersionConfig` 容错、`enabledDirectories` 不受影响。
- 清理:canonical 路径可命中宿主机目录(哈希一致性)。

## 验收回链

覆盖 PRD 全部验收项:变量配置通过校验、跨运行复用、绝对路径零迁移、危险前缀/穿越仍拒、
文档更新、单测覆盖。
