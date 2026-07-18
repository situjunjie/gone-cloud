# 修复 DingTalkPlugin Spring 构造函数注入失败

## Goal

修复 DevOps 服务启动时 `DingTalkPlugin` 无法被 Spring 创建的问题，恢复 Pipeline 插件注册表及其上游业务 Bean 的正常初始化。

## Background

- 应用启动失败链路为 `applicationController -> applicationServiceImpl -> pipelineSpecValidationServiceImpl -> pipelinePluginRegistry -> dingTalkPlugin`。
- 根因异常为 `NoSuchMethodException: DingTalkPlugin.<init>()`。
- `DingTalkPlugin` 同时声明公开的 `DingTalkRobotClient` 构造函数和包级的 `DingTalkRobotClient + Clock` 测试构造函数，但没有显式标注 Spring 应使用的构造函数。
- 现有纯单元测试直接调用构造函数，因此未覆盖 Spring 容器的 Bean 实例化过程。

## Requirements

- R1. 显式声明 Spring 使用 `DingTalkRobotClient` 构造函数完成生产依赖注入。
- R2. 保留可注入固定 `Clock` 的测试能力，不改变签名算法、插件配置或执行行为。
- R3. 增加轻量 Spring 容器回归测试，证明提供 `DingTalkRobotClient` Bean 时能够创建 `DingTalkPlugin`。
- R4. 运行插件聚焦测试和 DevOps 模块编译，确认没有引入新的 Bean 或编译回归。

## Acceptance Criteria

- [x] AC1. Spring 容器不再尝试调用 `DingTalkPlugin` 无参构造函数。
- [x] AC2. `DingTalkPlugin` 能通过容器注入 `DingTalkRobotClient` 并成功创建。
- [x] AC3. 原有 DingTalk 消息、签名和 HTTP 客户端测试继续通过。
- [x] AC4. DevOps server 模块编译通过。

## Out Of Scope

- 修改 Pipeline 插件 SPI、DSL 或钉钉消息格式。
- 修改其他 Bean 的注入方式。
