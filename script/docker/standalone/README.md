# Yudao 多服务可选部署

这套部署文件面向当前仓库的多个 Java 服务。

目标是：

- 按服务列表选择性构建
- 按服务列表选择性部署
- MySQL、Redis、Nacos、XXL-Job 等基础依赖通过 `.env` 接入服务器已有实例

## 当前支持的服务

- `yudao-server`
- `gateway-server`
- `system-server`
- `infra-server`
- `bpm-server`

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

3. 如需 DevOps 表结构，导入：

```bash
sql/mysql/devops.sql
```

4. Jenkins 执行流水线。

## Jenkins 参数

- `SERVICES`
  - 逗号分隔服务名
  - 示例：`gateway-server,system-server,infra-server`
  - 填 `all` 表示全部服务
- `DEPLOY_DIR=/opt/gone-cloud/services`
- `IMAGE_REPO_PREFIX=gone-cloud`
- `SKIP_TESTS=true`
- `DEPLOY_NOW=true`

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
6. `member/pay/report/mp/mall/crm/erp/iot/mes/wms/im/ai` 等脚手架模块当前已从根 `pom.xml` 注释，不参与默认构建
