# K8s 构建集群 —— 界面自测注意事项

> 面向"通过管理界面手工触发流水线"的自测场景。
> 代码修复记录见 [.trellis/tasks/08-31-devops-global-config-build-cluster/notes.md](../.trellis/tasks/08-31-devops-global-config-build-cluster/notes.md)。
> 集群侧完整测试方案见 [K8S_BUILD_TEST_PLAN.md](./K8S_BUILD_TEST_PLAN.md)（该文档部分内容已过期，以本文为准）。

---

## 0. 开始前必须做的两件事

这两项不处理，测试一定失败。

### 0.1 配置 `kaniko-image`（否则 Kaniko 构建必失败）

`application.yaml` 里**没有** `kaniko-image`，会回退到默认值 `gcr.io/kaniko-project/executor:v1.23.2-debug`，
而本集群**无法访问 gcr.io**。启动前必须加上：

```yaml
devops:
  config:
    build:
      cluster:
        kaniko-image: gcr.m.daocloud.io/kaniko-project/executor:v1.23.2-debug
```

`yudao-server/src/main/resources/application.yaml` 和
`yudao-module-devops/.../application.yaml` 两处都要改（取决于你启动哪个）。

### 0.2 清理 Nacos `gone` 命名空间的僵尸实例

`gone` 命名空间里注册了一批**不可达但 healthy=true** 的实例：

| 服务 | 注册地址 | 实际可达 |
|---|---|---|
| system-server | 172.16.1.5:48081 | 不可达 |
| devops-server | 172.16.1.6:48094 | 不可达 |
| gateway-server | 172.16.1.3:48080 | 不可达 |
| infra-server | 172.16.1.7:48082 | 不可达 |

已在集群内部（Pod 里）验证过，这些地址同样不可达。网关用 `grayLb://` 按权重**随机**选实例，
所以症状是**偶发 502 / 超时**，很难复现定位。

处理方式二选一：

- 测试前把这些实例从 Nacos 摘掉；
- 或确认本地服务注册后权重能压过它们。

> 参考：单模块本地启动时可用
> `--spring.cloud.openfeign.client.config.<服务名>.url=http://127.0.0.1:<端口>`
> 强制直连，绕开注册中心（本次集群验收就是这么绕过的）。

---

## 1. 执行器与镜像选择

### 1.1 不要用 `local-docker/*`

本机 Docker daemon 当前**未运行**（`docker version` 连不上 socket）。
界面里 `runsOn.group` 选 `local-docker/xxx` 会失败。本次全部使用：

```yaml
runsOn:
  group: kubernetes/build-cluster
  container: <镜像>
```

### 1.2 镜像选择：优先用官方镜像（自带 git）

实测结论（2026-10-10，本集群拉取验证）：

| 镜像 | git | java | node | mvn | 说明 |
|---|---|---|---|---|---|
| `alpine/git:latest` | ✅ 2.54 | — | — | — | 小，纯 shell / git 场景 |
| `maven:3.8-openjdk-17` | ✅ 2.27 | ✅ 17 | — | ✅ 3.8.5 | **自带 git，Java 17 推荐** |
| `maven:3.9-eclipse-temurin-21` | ✅ 2.43 | ✅ 21 | — | ✅ 3.9.16 | **自带 git，Java 21 推荐** |
| `node:20`（非 slim） | ✅ 2.39 | — | ✅ 20 | — | **自带 git，前端推荐** |
| `node:20-bookworm-slim` | ❌ | — | ✅ 20 | — | slim 版**无 git** |
| `eclipse-temurin:21-jdk` | ❌ | ✅ 21 | — | — | **无 git** |

**关键点：`maven:*` 和 `node:*`（非 slim）官方镜像自带 git**，而 `eclipse-temurin`、`*-slim` 没有。
Checkout 步骤是在 Pod 内跑 git 的，选错镜像会在检出阶段失败。

结论：**不同 JDK / Node 版本直接用对应的官方镜像即可，不需要自己维护镜像**。
详见第 5 节"通用镜像方案"。

### 1.3 资源配额

当前配置 request `1000m / 8Gi`，limit `2000m / 16Gi`。
Maven 大项目可能触发 OOM，若失败先看 Pod 是否被 OOMKilled。

---

## 2. 集群侧前置状态（已就绪）

| 项 | 状态 |
|---|---|
| `devops-build` 命名空间 | ✅ 存在 |
| `pipeline-shared-workspace` PVC（100Gi / local-path / RWO） | ✅ Bound |
| `git-credentials` Secret | ✅ 已创建，**请勿删除** |
| CoreDNS 内网解析 `gitlab.gigimed.cn` | ✅ 已修复（`kube-system/coredns-custom`） |
| `alpine/git`、`maven:*`、`node:*` 可拉取 | ✅ 已验证 |
| Kaniko 镜像可拉取 | ✅ 已验证（走 daocloud 源） |

⚠️ **`local-path` 是 RWO 单节点存储**，Pod 必须调度到卷所在节点。
当前是单节点集群（`onion-ubuntu`）无影响；将来加节点需改用支持 RWX 的存储类。

---

## 3. 界面里写 YAML 的关键点

### 3.1 KanikoBuild 必须显式加两个参数

Nexus 是 **HTTP** 且集群**拉不到 docker.io**，缺任一项都会失败：

```yaml
step: KanikoBuild
with:
  dockerfile: Dockerfile
  context: .
  imageTag: 192.168.16.102:8082/gigi-docker/${APP_KEY}:${PIPELINE_RUN_ID}
  registry: 192.168.16.102:8082
  registryUsername: user
  registryPassword: user@nexus
  insecure: true                        # ← 不加必失败（Nexus 是 HTTP）
  registryMirror: docker.m.daocloud.io  # ← 不加则拉不到 alpine:latest 等基础镜像
  cache: false
  timeoutSeconds: 900
```

### 3.2 源码检出

- 应用已关联仓库时**会自动隐式 checkout**；
- 独立流水线需显式声明 `sources` 或加 `Checkout` 步骤；
- **一个 Job 最多一个 KanikoBuild**。

### 3.3 跨 Job 共享工作区

当前是 `SHARED_PVC`，已验证可用（后一个 Job 能读到前一个 Job 写入的文件）。
**`EMPTY_DIR` 模式不跨 Pod 保存数据**，不要指望。

---

## 4. 看起来像 bug 但不是的现象

1. **Pod 跑完立刻消失** —— 当前是 `IMMEDIATE` 清理策略，正常。想看 Pod 需改成 `RETAIN`。
2. **同一应用环境不能并发触发** —— 有 `validateNoActivePipelineRun` 校验，属设计。并发测试需用不同应用环境。
3. **日志里 stderr / stdout 分不出来** —— **既有实现问题**（`PipelineLogStorageServiceImpl.readRawLines`
   把 streamType 硬编码为 `stdout`），非本次改动引入，不要当新 bug 查。
4. **审批 / K8sDeploy 步骤不产生 Pod** —— 这些在平台侧执行，本来就不进构建集群。
5. **`Checkout` 与 `CodeMerge` 行为差异** —— `CodeMerge` 会做合并并可能推送；只想拉代码用 `Checkout`。

---

## 5. 通用镜像方案（不同 JDK / Node 版本怎么处理）

### 5.1 先明确约束

- `runsOn.container` 是 **Job 级**的：一个 Job 内所有步骤共用同一个 Pod / 同一个镜像，
  **不能在步骤级别换镜像**。
- 所以"换工具链版本"= 换 Job 的镜像，或者在镜像内部运行时切换。
- 平台已有依赖缓存机制，默认缓存目录（会自动挂到缓存 PVC 的隔离子目录）：
  `/root/.m2`、`/root/.gradle/caches`、`/root/.npm`、`/root/.pnpm-store`、`/root/.yarn`、`/go/pkg/mod`。

### 5.2 推荐：直接用官方镜像（零维护，首选）

既然 `maven:*` / `node:*` 官方镜像自带 git，**不同版本直接换镜像即可**，不需要自己构建：

```yaml
# Java 8
container: maven:3.8-openjdk-8
# Java 17
container: maven:3.8-openjdk-17
# Java 21
container: maven:3.9-eclipse-temurin-21
# Node 18
container: node:18
# Node 20
container: node:20
```

优点：
- **零维护**，官方持续更新安全补丁；
- 版本组合不需要预先规划，随时可用；
- 缓存 PVC 已覆盖 `.m2` / `.npm` 等，依赖下载不会重复。

注意：
- 避免 `*-slim` 和 `eclipse-temurin`（无 git），除非该 Job 不需要 Checkout；
- 若 Job 需要额外工具（kubectl / jq / 自定义 CLI），官方镜像没有，见 5.3。

### 5.3 需要额外工具时：单个"工具层"镜像 + SDKMAN/fnm 运行时切换

如果确实要"一个镜像覆盖多版本"，推荐**运行时版本管理器**而非预装全部版本：

- **JDK / Maven / Gradle**：用 [SDKMAN](https://sdkman.io/)，`sdk install java 21.0.1-tem` 按需下载；
- **Node**：用 [fnm](https://github.com/Schniz/fnm) 或 nvm，支持 `.nvmrc` 自动切换。

镜像里只装 SDKMAN/fnm + 常用工具（git、kubectl、jq、docker-cli），
然后把工具链安装目录加入缓存目录配置，让版本切换**只下载一次**：

```yaml
# 流水线缓存配置中追加（界面"缓存目录配置"或 cacheConfig.directories）
- id: sdkman
  path: /root/.sdkman/candidates
  description: SDKMAN 工具链（JDK / Maven / Gradle）
- id: fnm
  path: /root/.local/share/fnm
  description: fnm Node 版本
```

Job 里指定版本：

```yaml
runsOn:
  group: kubernetes/build-cluster
  container: registry.gigimed.cn/devops/pipeline-builder:base
steps:
  build:
    step: Command
    with:
      run: |
        sdk install java 21.0.1-tem   # 首次下载，之后命中缓存
        sdk use java 21.0.1-tem
        fnm use 20
        mvn -B clean package
```

优点：镜像体积可控（~1.5GB），版本任意组合，新增版本无需重建镜像。
缺点：首次使用某版本要联网下载（之后走缓存）；依赖 `sdk use` 在每个步骤里显式声明。

### 5.4 不推荐的做法

| 做法 | 问题 |
|---|---|
| 预装所有 JDK/Node 版本到一个镜像 | 体积 4GB+，拉取慢、更新困难 |
| 每个版本组合一个镜像 | 组合爆炸（JDK×Node×Maven），违背"不想维护多个镜像" |
| initContainer 注入工具链 | 实现复杂，收益不明显 |

### 5.5 选型建议

| 场景 | 建议 |
|---|---|
| 版本需求固定、每类 1~2 个 | **方案 5.2**（官方镜像），零维护 |
| 版本需求多变、需要额外工具 | **方案 5.3**（单镜像 + SDKMAN/fnm + 缓存） |
| 混合 | 多数 Job 用官方镜像；个别需要多版本切换的用 5.3 |

---

## 6. 建议的测试顺序

由易到难，便于定位问题：

1. **基础命令** —— `alpine/git:latest` + `echo`，确认 Pod 能起、日志能出；
2. **显式 Checkout** —— 确认 git 凭据与内网 DNS 通；
3. **跨 Job 共享** —— 两个 Job，第二个 `cat` 第一个写的文件；
4. **Maven 构建** —— `maven:3.8-openjdk-17`；
5. **Kaniko 推镜像** —— 最后用下列命令核实镜像**真的**推上去了：
   ```bash
   curl -u user:user@nexus \
     http://192.168.16.102:8082/v2/gigi-docker/<app>/tags/list
   ```

---

## 7. 测试数据与遗留物

### 7.1 已存在的测试数据（可复用 / 可清理）

- 应用环境：`59`（K8s构建集群E2E）、`63` / `64`（并发测试）
- 测试流水线定义：id `33` / `34` / `35`
- 测试镜像：`192.168.16.102:8082/gigi-docker/k8s-e2e-test:1051`
- 共享 PVC 上残留目录：`runs/1052`~`runs/1057` 等（约 100MB/个）

### 7.2 集群级改动（需确认是否保留）

1. **CoreDNS 自定义 server block**（`kube-system/coredns-custom`）：
   把 `gitlab / registry / docker.gigimed.cn` 指向 `192.168.16.102`。
   **建议保留** —— 否则 Pod 内解析到公网 IP，git 拉取超时。影响整个集群 DNS，请确认。
2. CoreDNS 被 rollout restart 过（当前 1/1 Running，正常）。
3. `devops-build/git-credentials` Secret —— 正式跑流水线需要，**勿删**。

---

## 8. 已知未覆盖的场景

以下本次集群验收**未验证**，自测时请重点关注：

- 冲突挂起与恢复（`CodeMerge` 冲突）
- 审批恢复后的源码标记
- `DYNAMIC_PVC` / `EMPTY_DIR` 两种存储模式
- 跨节点 RWX 存储场景
- 长时间运行稳定性与资源泄漏
- 多租户隔离（构建集群全局配置目前只支持单个 `environmentId`）
