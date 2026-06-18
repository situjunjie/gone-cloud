# HOST 主机组环境第一步

## Goal

将 DevOps 模块中当前占位的 `infraType=HOST` 环境落地为“主机组环境”的第一步能力：一个 HOST 环境可以维护多个 SSH 主机，并支持主机 CRUD 与单主机连接检测。终端连接、主机资源详情、活跃进程和环境大盘在后续步骤实现。

## What I already know

- `EnvironmentInfraTypeEnum` 已存在 `HOST` 类型。
- 当前 `EnvironmentServiceImpl#buildInfraConfig` 对 HOST 直接返回 `null`，HOST 还没有连接器和主机子资源。
- 项目规范要求基础设施差异通过 `EnvironmentConnector` 边界扩展，HOST/SSH/host-group 不应写入通用环境 CRUD 逻辑。
- `dev_environment.infra_config` 已是加密 JSON 字段，K8S 和 Docker 均通过 connector 构建配置。
- 新增主机连接凭据属于敏感字段，必须加密存储，响应 VO 不返回密码、私钥、passphrase 原文。

## Requirements

- 新增 HOST 环境连接器，让 `infraType=HOST` 通过 `EnvironmentConnectorFactory` 正常处理。
- 新增主机子资源表，用于保存 HOST 环境下的多个主机。
- 主机字段至少包含：环境编号、主机标识、主机名称、地址、端口、用户名、认证方式、密码/私钥/passphrase、状态、备注、最近检测信息。
- 同一环境内 `hostKey` 唯一。
- 只允许在 `infraType=HOST` 的环境下创建、修改、查询、删除主机。
- 支持主机分页/列表查询。
- 支持单主机 SSH 连接检测，并记录最近检测状态、时间和脱敏后的错误信息。
- 支持 HOST 环境整体连接检测，返回主机总数、成功数、失败数和可读消息。
- 响应 VO 不泄露 SSH 密码、私钥、passphrase。
- 连接检测只做认证和基础连通性验证，不执行用户传入命令。

## Acceptance Criteria

- [ ] `POST /devops/environment/host/create` 可在 HOST 环境下创建主机。
- [ ] `PUT /devops/environment/host/update` 可修改主机，省略敏感字段时保留旧值。
- [ ] `DELETE /devops/environment/host/delete?id=` 可删除主机。
- [ ] `GET /devops/environment/host/page` 可分页查询某 HOST 环境主机。
- [ ] `POST /devops/environment/host/check?id=` 可检测单主机 SSH 连接。
- [ ] `POST /devops/environment/check?id=` 对 HOST 环境返回主机组连接检测汇总。
- [ ] 非 HOST 环境调用主机接口返回基础设施类型不支持。
- [ ] 响应中只返回 `credentialConfigured` 等安全派生字段，不返回密钥原文。
- [ ] DevOps server 模块可编译，相关单元测试通过或至少新增 focused tests。

## Out of Scope

- 环境大盘前端页面。
- 定时采集、历史指标、告警。
- 批量命令执行和主机批量操作。

## Technical Notes

- 按 `.trellis/spec/backend/devops-infra-guidelines.md` 的 connector boundary 实现。
- 按 `.trellis/spec/backend/database-guidelines.md` 的 `TenantBaseDO` + `BaseMapperX` + mapper default method 模式实现。
- SSH 库优先选择 Apache MINA SSHD client，依赖版本需放到 `yudao-dependencies/pom.xml` 管理。

## Phase 2 Addendum: 主机详情与 Web SSH 终端

### Goal

在第一期 HOST 主机组 CRUD + SSH 检测基础上，补齐后端可观测与操作入口：提供 HOST 环境主机组大盘、单主机详情、活跃进程列表和 Web SSH 终端 WebSocket。前端页面实现不在本次后端任务内，但需要在完成后提供详细接入 prompt。

### Requirements

- 新增 HOST 环境大盘接口，返回主机总数、在线/异常/未检测统计和主机摘要列表。
- 新增主机详情接口，通过 SSH 执行后端固定只读命令采集系统信息、CPU、负载、内存、磁盘和活跃进程。
- 新增主机活跃进程接口，支持 `limit` 限制返回数量。
- 新增 Web SSH 终端 WebSocket，复用现有 K8s/Docker 终端消息协议：`input`、`resize`、`close`、`output`、`error`、`closed`。
- 终端连接必须校验环境存在、环境类型为 HOST、主机存在且属于该环境。
- 主机详情采集不接受前端传任意命令，所有命令固定在后端。
- 终端输入输出不写业务日志，不返回或记录 SSH 密码、私钥、passphrase。

### Acceptance Criteria

- [ ] `GET /devops/environment/host/dashboard?envId=` 返回 HOST 主机组大盘。
- [ ] `GET /devops/environment/host/detail?id=` 返回单主机系统基础信息、CPU、负载、内存、磁盘、进程和采集时间。
- [ ] `GET /devops/environment/host/processes?id=&limit=` 返回活跃进程列表。
- [ ] `WS /devops/host/terminal?environmentId=&hostId=` 可打开交互式 SSH shell。
- [ ] 非 HOST 环境或主机不属于环境时，接口返回标准业务错误。
- [ ] 主机详情采集失败时返回 `connected=false` 和脱敏错误信息，不泄露凭据。
- [ ] 编译通过，新增 focused tests 覆盖指标解析、详情失败分支和终端服务校验。

### Still Out of Scope

- 前端页面代码。
- 历史指标、定时采集、告警。
- 任意命令执行 HTTP API。
- 批量命令执行和批量主机操作。
