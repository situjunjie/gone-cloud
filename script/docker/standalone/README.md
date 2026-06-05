# Yudao 多服务可选部署

这套部署文件面向当前仓库的多个 Java 服务。

目标是：

- 按服务列表选择性构建
- 按服务列表选择性部署
- MySQL、Redis、Nacos、XXL-Job 等基础依赖通过 `.env` 接入服务器已有实例

## 当前支持的服务

根目录 `Jenkinsfile` 当前只构建和部署根 `pom.xml` 中已启用的服务：

- `yudao-server`
- `gateway-server`
- `system-server`
- `infra-server`
- `bpm-server`

`docker-compose.yml` 中保留了其它脚手架服务的部署模板，但这些模块当前在根 `pom.xml` 中被注释，不能直接通过 Jenkins 的 `SERVICES=all` 构建。

## 包含内容

- `docker-compose.yml`
  - 多服务部署模板
- `.env.example`
  - 外部依赖和端口映射样例
- 根目录 `Jenkinsfile`
  - 按服务列表构建 jar
  - 按服务列表构建镜像
  - 按服务列表执行 docker compose 部署

## 首次部署

1. 在服务器准备部署目录，例如：

```bash
mkdir -p /opt/gone-cloud/services
```

2. 把 `.env.example` 复制成 `.env` 并按服务器实际配置修改：

```bash
cp script/docker/standalone/.env.example /opt/gone-cloud/services/.env
```

`.env` 需要填写外部提供的 MySQL、Redis、Nacos、XXL-Job 等基础设施地址。Jenkins 会同步最新的 `docker-compose.yml`、`README.md`、`.env.example` 到部署目录，但不会创建或覆盖真实 `.env`。

3. 如需 DevOps 表结构，导入：

```bash
sql/mysql/devops.sql
```

4. Jenkins 执行流水线。

## Jenkins 参数

- `SELECT_ALL_SERVICES=false`
  - 勾选后全选当前已启用服务
- 服务复选框
  - `SERVICE_YUDAO_SERVER=true`
  - `SERVICE_GATEWAY_SERVER=false`
  - `SERVICE_SYSTEM_SERVER=false`
  - `SERVICE_INFRA_SERVER=false`
  - `SERVICE_BPM_SERVER=false`
  - 未勾选全选时，Jenkins 只构建和部署已勾选的服务
- `DEPLOY_DIR=/opt/gone-cloud/services`
- `IMAGE_REPO_PREFIX=gone-cloud`
- `MAVEN_TOOL_NAME=maven`
  - Jenkins 全局 Maven 工具名称，必须和 Jenkins「Global Tool Configuration」里的 Maven Name 完全一致
  - 留空时跳过 Jenkins 全局 Maven，继续尝试 `MAVEN_CMD`、节点 PATH 中的 `mvn`、Dockerized Maven
- `MAVEN_CMD=`
  - `MAVEN_TOOL_NAME` 留空或 Jenkins 全局 Maven 名称解析失败时生效
  - 可填完整 Maven 命令，例如 `/opt/maven/bin/mvn`
- `MAVEN_DOCKER_IMAGE=maven:3.9.9-eclipse-temurin-17`
  - Jenkins 全局 Maven、`MAVEN_CMD`、节点 `mvn` 都不可用时使用
- `SKIP_TESTS=true`
- `DEPLOY_NOW=true`
  - `true`：构建 Jar、构建 Docker 镜像，并部署所选服务
  - `false`：只构建 Jar 和 Docker 镜像，不执行 compose 部署

部署阶段会在 `DEPLOY_DIR` 内执行：

```bash
docker compose --env-file .env -f docker-compose.yml up -d <services>
```

因此 `logs/`、`plugins/` 等相对挂载目录都会落在 `DEPLOY_DIR` 下。

## 手工部署示例

只部署网关、系统、基础设施三个服务：

```bash
mvn -pl yudao-gateway,yudao-module-system/yudao-module-system-server,yudao-module-infra/yudao-module-infra-server -am clean package -DskipTests

docker build -t gone-cloud/yudao-gateway:latest -f yudao-gateway/Dockerfile yudao-gateway
docker build -t gone-cloud/yudao-module-system-server:latest -f yudao-module-system/yudao-module-system-server/Dockerfile yudao-module-system/yudao-module-system-server
docker build -t gone-cloud/yudao-module-infra-server:latest -f yudao-module-infra/yudao-module-infra-server/Dockerfile yudao-module-infra/yudao-module-infra-server

cp script/docker/standalone/docker-compose.yml /opt/gone-cloud/services/docker-compose.yml
cp script/docker/standalone/.env.example /opt/gone-cloud/services/.env

cd /opt/gone-cloud/services
docker compose --env-file .env -f docker-compose.yml config --quiet
docker compose --env-file .env up -d gateway-server system-server infra-server
```

## 关键说明

1. `yudao-server` 是单体服务
2. `gateway-server` 和各 `*-server` 是微服务拆分模式
3. 如果使用微服务拆分模式，通常需要：
   - `gateway-server`
   - `system-server`
   - `infra-server`
   - 以及你实际启用的业务服务
4. 微服务模式下，需要可用的 Nacos
5. 当前 `.env` 默认通过 `host.docker.internal` 访问宿主机上的基础容器；如果你的基础容器和这些服务在同一 Docker 网络，也可以改成容器名
6. XXL-Job 的 token 环境变量使用 `XXL_JOB_ACCESS_TOKEN`，旧的 `XXL_JOB_ACCESSTOKEN` 仍兼容
7. `member/pay/report/mp/mall/crm/erp/iot/mes/wms/im/ai` 等脚手架模块当前已从根 `pom.xml` 注释，不参与默认构建
