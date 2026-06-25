# Implement — Pipeline Cache Path Workspace-Relative Support

按 design.md 决策执行。方向 1:`${WORKSPACE}` 变量;canonical(保留字面量)作哈希/元数据/清理键,
resolved 仅用于 Docker bind 与容器 workingDir。绝对路径零迁移,普通路径仍拒 `/workspace/...`。

## Step 1 — 抽取容器 workspace 常量

- 新增 `framework/pipeline/runtime/PipelineWorkspaceConstants.java`,定义
  `public static final String CONTAINER_WORKSPACE = "/workspace"`。
- `DockerPipelineJobRuntimeManager` 改引用该常量(替换本地 `CONTAINER_WORKSPACE`,`:31`/`:54`/`:63`)。
- 目的:让 `PipelineCacheConfigResolver` 解析变量时无需反向依赖 runtime 管理器。

## Step 2 — `PipelineCacheMount` 新增 canonicalPath

- 字段:`canonicalPath`(规范存储路径,可含 `${WORKSPACE}`)、`containerPath`(解析后挂载点)。
- 更新 builder 调用点(Step 4)。

## Step 3 — `PipelineCacheConfigResolver` 变量解析与校验

- 新增已知变量表:`${WORKSPACE}` -> `PipelineWorkspaceConstants.CONTAINER_WORKSPACE`。
- 新增 `public String resolveContainerPath(String canonicalPath)`:把开头的 `${WORKSPACE}`
  替换为 `/workspace`,非变量路径原样返回。
- `normalizeDirectory` / `normalizePath`:在保留变量字面量前提下规范化(trim、合并 `//`、去尾 `/`)。
- `validatePath` 改造:
  - 变量形式(以 `${WORKSPACE}` 开头):
    - 解析 `resolved = "/workspace" + remainder`,`remainder` 必须非空,否则报
      `PIPELINE_CACHE_CONFIG_INVALID`("缓存目录不能是整个工作目录")。
    - 对 canonical 与 resolved 做路径穿越检查(复用现有 `/../`、`/./`、`/..`、`/.` 规则)。
    - 跳过 `/workspace` 前缀拒绝;resolved 仍校验其余 `FORBIDDEN_PREFIXES`
      (`/proc`、`/sys`、`/dev`、`/run`、`/tmp`)。
    - 拒绝未知变量(`${...}` 命中非已知表)→ "未知缓存变量"。
  - 普通绝对路径形式:维持现状全部规则(继续拒绝 `/workspace` 前缀)。
  - `MAX_PATH_LENGTH` 作用于 canonical。
- `normalizeOrDefault`:重复检查与 `rejectNestedEnabledPaths` 改用 **resolved containerPath**
  比较(canonical 仍可作 map 键,额外用 resolved 集合判嵌套/重复)。
- `pathHash` 入参保持"传入即哈希",调用方改传 canonical(见 Step 4)。
- `schemaVersion` 默认常量由 `1.0` -> `1.1`。

## Step 4 — `LocalPipelineWorkspaceService` 挂载与持久化

- `createWorkspace`(`:50-57`)mount 构建:
  - `canonical = directory.getPath()`
  - `containerPath = resolver.resolveContainerPath(canonical)`
  - `hostPath = resolveCachePath(cacheWorkspace, canonical)`(哈希入参由 path 改 canonical)
  - mount 同时 set canonicalPath 与 containerPath。
- `recordCachePaths`(`:66`、`:175-191`):记录 canonical(`cacheMounts.stream().map(canonicalPath)`)。
- `resolveCachePath`(`:171-173`)入参语义=canonical;`clearDefinitionCache`(`:86-102`)入参同为
  canonical,哈希一致,无需额外改动(仅形参/注释命名)。

## Step 5 — Docker bind 用 resolved

- `DockerPipelineJobRuntimeManager.java:58`:`new Bind(hostPath, new Volume(containerPath))`
  其中 containerPath 为解析后的容器路径。

## Step 6 — 清理流程命名(可选,低风险)

- `PipelineWorkspaceService.clearDefinitionCache` / 实现 / `PipelineDefinitionServiceImpl` 调用处
  形参与注释从 "containerPaths" 调整为 "cachePaths"(canonical 语义)。行为不变。

## Step 7 — 文档

- `PIPELINE_YAML_SPEC.md` cacheConfig 章节:`${WORKSPACE}` 变量、示例、错误码、schemaVersion 1.1。
- `PIPELINE_DOCKER_RUNTIME_DESIGN.md` 缓存章节:嵌套挂载示意、持久化作用域、易失性提示。

## Step 8 — 单测

参照 design.md 测试计划,覆盖 `PipelineCacheConfigResolver`:
- 合法变量路径:规范化保留字面量、`resolveContainerPath` 正确、默认配置不变。
- 拒绝:`${WORKSPACE}` 整目录、`${WORKSPACE}/../x` 穿越、`${FOO}/x` 未知变量、resolved 命中危险前缀。
- 普通绝对路径零变化;`/workspace/x` 直写仍拒。
- 嵌套:`${WORKSPACE}/a` 与 `${WORKSPACE}/a/b` 报错。
- hostPath 哈希一致性(canonical 派生),清理可命中。
- 找现有缓存相关测试类(若有)沿用其风格;无则新建。

## 验证

- 编译:`mvn -q -pl yudao-module-devops/yudao-module-devops-server -am test-compile`(或项目惯用)。
- 跑新增/相关单测。
- 清理临时文件。
