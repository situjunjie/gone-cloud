# 2026-09-07 构建集群修复记录

用户确认继续本任务，不创建新任务。原 PRD 仅覆盖配置类，后续用户需求已扩展为 Kubernetes Pod 构建环境；此记录保存扩展范围与实际验证结果。

## 已修复

- Pipeline 引擎按实际执行器选择 Docker/Kubernetes manager；通用命令通过路由器执行。
- K8s exec 获取真实退出码，流式日志、总超时、取消关闭连接并删除 Pod；修复取消与成功回调竞态。
- 创建 builder Pod；KanikoBuild 增加独立 debug Kaniko 容器，与 builder 共享 /workspace。
- Kaniko 支持 imageTag、registry 凭证、target、构建参数与超时，凭证临时保存并清理。
- K8s 的隐式源码准备、Checkout、CodeMerge 和冲突读写均在 Pod 内完成。Pod 身份可持久化恢复，kubeconfig/token 不写入恢复上下文。
- 本地目录、每个 EmptyDir Pod、同一 Run 的共享 PVC 分别记录源码准备状态。
- BLOCKED K8s Job 保留 Pod，恢复时复用并校验归属；Docker 保持原生命周期。
- 失败 RETAIN 使用 Job 结果，不依赖仍在 Running 的 Pod phase。
- 终态清理 Pod；动态 PVC 标记后在最后 Pod 消失时删除，定时回收处理异步删除和 RETAIN 到期。共享 PVC 不删除。
- 缓存子目录按租户、流水线 cacheKey、容器路径隔离。非 SHARED_PVC 模式需显式配置 cache-pvc-name 才挂载依赖缓存。
- 已取消的 Run 不因执行线程随后退出而被重新写为失败。
- 审批恢复后显式 Checkout 恢复源码标记；平台步骤独立准备本地源码，不继承 Pod 的源码标记。

## 当前流水线示例

以下采用实际解析器的 stages/jobs/steps 映射结构。构建镜像需要 sh、git 和对应构建工具；runs-on.container 是 builder 镜像，Kaniko 镜像由 devops.config.build.cluster.kaniko-image 指定。

```yaml
stages:
  build:
    name: Build
    jobs:
      package:
        name: Package
        runs-on:
          group: kubernetes/default
          container: maven:3.8-openjdk-17
        steps:
          compile:
            step: Command
            with:
              run: mvn clean package -DskipTests
          image:
            step: KanikoBuild
            with:
              dockerfile: Dockerfile
              context: .
              imageTag: registry.example.com/demo:build-1
              registry: registry.example.com
              registryUsername: ${REGISTRY_USERNAME}
              registryPassword: ${REGISTRY_PASSWORD}
              timeoutSeconds: 3600
```

应用关联仓库会隐式 checkout；独立流水线需声明 sources 或显式 Checkout。一个 Job 最多一个 KanikoBuild。跨 Job 传递未推送的源码或构建产物需要共享/动态 PVC；EmptyDir 不跨 Pod 保存数据。

## 验证

- `mvn -pl yudao-module-devops/yudao-module-devops-server test`：BUILD SUCCESS，DevOps server 471 tests，0 failures/errors/skipped。
- 包含真实本地 Git 脚本执行，以及 Mockito 模拟 Kubernetes API/ExecWatch 的回归测试；这不等于真实集群端到端验证。
- `git diff --check`：通过。

## 真实集群验收（2026-10-10，k3s 192.168.16.102 / devops-build）

本地单模块启动 devops-server，配合本机启动的 system-server/infra-server 完成鉴权链路，对真实集群跑通流水线。

### 已通过场景

- 基础命令 + 显式 Checkout：Pod 创建、拉取、`pods/exec` 流式日志、真实退出码、按 IMMEDIATE 清理 Pod 全部正常。
- 跨 Job 共享工作区：两个 Job 各自 Pod，第二个 Pod 能读到第一个写入的标记文件与源码，证明 SHARED_PVC + subPath 生效。
- 并发：同一时刻 3 个 Run / 3 个 Pod 并行执行，两个成功 Run 同时开始同时结束（79s），共享 PVC 上 `runs/<runId>` 目录与缓存子目录互不干扰。
- 失败处理：`exit 3` 原样上报，步骤 FAILED、Job FAILED、Run FAILED，Pod 按策略清理。
- 取消：运行中取消后 Run 置为已取消且 Pod 被删除。
- Kaniko 构建：debug Kaniko 容器与 builder 共享 /workspace，构建成功并把镜像推送到 Nexus（`192.168.16.102:8082/gigi-docker/k8s-e2e-test:1051`），仓库 `tags/list` 与 manifest 均可查到。
- RETAIN + 定时回收：失败 Pod 保留并打上 `devops.cleanup-after`，到期后由 `K8sBuildPodCleanupJob` 删除。

### 本轮修复的问题

1. 同一个 PVC 被同时声明为 workspace 与 cache 两个 Volume，kubelet 卷协调失败（`unmounted volumes / context deadline exceeded`），Pod 永远卡在 ContainerCreating。改为复用同一 Volume 名称、只追加挂载点。
2. K8s 运行时把容器内路径 `/workspace` 记为平台侧工作目录，导致平台写日志落到宿主机根目录并报 `Read-only file system`。改为记录平台侧 run workspace。
3. SHARED_PVC 的 `workspaceSubPath` 与 `cacheMappings` 只写进 metadata、从未被消费，命令实际跑在容器临时 `/workspace`，共享 PVC 与依赖缓存均未生效。改为用卷 `subPath` 直接把 run 子目录挂到 `/workspace`，并在容器启动脚本里把缓存路径软链到缓存 PVC 的隔离子目录。
4. Kaniko debug 镜像没有 `/tmp`，执行器写脚本失败。执行器在写入前补建 `/tmp`。
5. Kaniko 不支持内网 HTTP 仓库与 docker.io 镜像源，新增 `insecure`（`--insecure --skip-tls-verify`）与 `registryMirror`（`--registry-mirror`）参数。
6. `K8sBuildPodCleanupJob` 在调度线程里没有租户上下文，查环境直接抛 `TenantContextHolder 不存在租户编号`，RETAIN 到期 Pod 永不回收。改为忽略租户执行。

### 环境侧修复（非代码）

- 集群 CoreDNS 缺少 `gitlab.gigimed.cn` 内网映射，Pod 内解析到公网 IP 导致 git 拉取超时；已通过 CoreDNS 自定义 server block 指向 `192.168.16.102`。
- 集群无法直连 `gcr.io`，Kaniko 镜像改用 `gcr.m.daocloud.io` 镜像源；`devops.config.build.cluster.kaniko-image` 需按环境配置。
- Nexus 仓库为 HTTP，需在 KanikoBuild 步骤显式设置 `insecure: true`。

### 尚未覆盖

- 冲突挂起与恢复、审批恢复后的源码标记、动态 PVC（DYNAMIC_PVC）与 EmptyDir 两种存储模式、跨节点 RWX 场景、长时间稳定性与资源泄漏。
- 回读日志时 streamType 恒为 `stdout`（`PipelineLogStorageServiceImpl.readRawLines` 硬编码），stderr 与 stdout 无法区分；属既有实现，未在本次修复。
- 构建集群全局配置目前只支持单个 environmentId；多租户/多集群需另行设计。

任务保持 in_progress，未提交代码、未部署、未归档。
