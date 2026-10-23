# Pipeline Deployer Image

DevOps 流水线 SSH 部署镜像，在 `gone-cloud/pipeline-builder:java17-node24-maven3.9` 基础上补充：

- `openssh-clients`（基础镜像不含 ssh/scp，SSH 部署步骤必需）
- `/usr/local/bin/deploy-compose-service`：容器侧入口。读环境变量 → 校验产物 → 建临时私钥 → 上传 jar → 触发远端部署
- `/usr/local/bin/deploy-compose-service-remote`：目标机侧脚本。备份旧包 → 原子替换 jar → `docker compose restart` → 健康检查 → 失败自动回滚

用于「构建产物是 jar、目标机用 docker-compose 起容器、jar 通过 volume 挂载进容器」这类部署场景。

## Build

```bash
docker build -t gone-cloud/pipeline-deployer:ssh \
  yudao-module-devops/docker/pipeline-deployer
```

## Verify

```bash
docker run --rm gone-cloud/pipeline-deployer:ssh sh -lc \
  'ssh -V && which deploy-compose-service deploy-compose-service-remote && java -version && mvn -version'
```

## 目标机前置条件

1. 存在 `DEPLOY_PATH`（jar 目录）与 `COMPOSE_DIR`（含 `docker-compose.yml`）。
2. `docker compose`（插件）或 `docker-compose`（v1）至少有一个可用。
3. 目标服务在 compose 中把 jar 以 volume 挂进容器，例如：

   ```yaml
   gigi-order-service:
     image: eclipse-temurin:17-jre
     volumes:
       - ./app.jar:/app/app.jar:ro
     command: ["java", "-jar", "/app/app.jar"]
     restart: unless-stopped
   ```

   若 compose 是把 jar `COPY` 进镜像的（如本仓库 `script/docker/standalone/docker-compose.yml`），替换宿主机 jar 后 `restart` **不会生效**，需要改走「构建镜像 + 推送 registry + `up -d`」的方案。
4. 目标机 `HEALTH_URL` 使用 `curl` 时需安装 curl；`KEEP_BACKUPS > 0` 时依赖 `ls -1t` 与 `tail`；`flock` 缺失时脚本会跳过并发保护并打告警。

## 环境变量契约

| 变量 | 必需 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `DEPLOY_HOST` | 是 | - | 目标机地址 |
| `DEPLOY_USER` | 否 | `root` | SSH 用户 |
| `DEPLOY_PORT` | 否 | `22` | SSH 端口 |
| `DEPLOY_PATH` | 是 | - | 目标机 jar 目录，如 `/data/gigi/order-service` |
| `COMPOSE_DIR` | 是 | - | `docker-compose.yml` 所在目录 |
| `COMPOSE_SERVICE` | 是 | - | compose service 名 |
| `SSH_PRIVATE_KEY_B64` | 是 | - | 私钥 base64 单行（变量组按 `KEY=VALUE` 行解析，无法存多行私钥） |
| `SSH_KNOWN_HOSTS_ENTRY` | 否 | 空 | 目标机 host key 行，配置后启用 `StrictHostKeyChecking=yes` |
| `ARTIFACT_FILE` | 否 | `jobs/maven_build_job/artifacts/app.jar` | 产物路径，相对 `/workspace` |
| `JAR_NAME` | 否 | `app.jar` | 目标 jar 文件名 |
| `HEALTH_URL` | 否 | 空 | 空则退化为检查容器状态为 `running` |
| `HEALTH_TIMEOUT_SECONDS` | 否 | `120` | 健康检查超时 |
| `LOCK_FILE` | 否 | `/tmp/deploy-<service>.lock` | 目标机 flock 文件 |
| `KEEP_BACKUPS` | 否 | `5` | 保留 `app.jar.bak.<时间戳>` 份数 |

变量组漏配时 `PipelineVariableResolver` 不会报错，占位符 `${X}` 会原样进入 env；`deploy-compose-service` 会检测 `*'${'*` 并直接失败，避免把字面量当值用。

## 生成私钥 base64

```bash
base64 -w0 ~/.ssh/id_ed25519_deploy     # macOS: base64 -i ~/.ssh/id_ed25519_deploy
```

## 单独调试远端脚本

不跑流水线也能验证目标机侧逻辑：

```bash
ssh root@HOST "sh -s -- /data/gigi/order-service app.jar /data/gigi/order-service gigi-order-service \
  http://127.0.0.1:48081/actuator/health 120 /tmp/deploy-order.lock 5" \
  < yudao-module-devops/docker/pipeline-deployer/deploy-compose-service-remote
```

前提是先把待部署的 jar 放到 `${DEPLOY_PATH}/${JAR_NAME}.tmp`。
