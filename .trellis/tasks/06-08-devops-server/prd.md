# 修复 devops-server 独立启动安全配置

## Goal

修复 `devops-server` 独立部署启动时报 `java.util.List` Bean 缺失的问题，让 DevOps 拆分服务和其它模块一样提供 Spring Security 自定义权限配置。

## What I already know

* 部署日志主错误为：`A component required a bean of type 'java.util.List' that could not be found.`
* `nacosGracefulShutdownDelegate` 是启动失败后的关闭流程 WARN，不是根因。
* 框架 `YudaoWebSecurityConfigurerAdapter` 使用 `@Resource private List<AuthorizeRequestsCustomizer> authorizeRequestsCustomizers;`。
* 其它模块如 system、infra、member 都有 `framework/security/config/SecurityConfiguration.java`，声明对应的 `AuthorizeRequestsCustomizer` Bean。
* `yudao-module-devops-server` 当前没有自己的 `SecurityConfiguration` 和 `AuthorizeRequestsCustomizer`。

## Assumptions

* DevOps 服务独立启动时至少需要一个 `AuthorizeRequestsCustomizer` Bean。
* DevOps 模块暂时没有内部 RPC `ApiConstants.PREFIX` 需要放行。
* 本次只补齐安全配置，不调整部署 env 和 Nacos 配置。

## Requirements

* 在 `yudao-module-devops-server` 增加模块级 `SecurityConfiguration`。
* 声明名为 `devopsAuthorizeRequestsCustomizer` 的 `AuthorizeRequestsCustomizer` Bean。
* 放行 Swagger、Actuator、Druid 路径，与其它模块保持一致。
* 代码风格参考现有模块安全配置。

## Acceptance Criteria

* [x] `devops-server` 编译通过。
* [x] 新增安全配置与 system/infra 模块模式一致。
* [x] 不引入无关配置改动。

## Definition of Done

* Maven compile/test for affected module passes, or failure原因明确。
* 工作树变更范围仅限当前任务相关文件。

## Out of Scope

* 不调整 Docker Compose 或环境变量映射。
* 不改 Nacos 配置。
* 不处理后续可能暴露出的 DB/Redis/Nacos 连接问题。

## Technical Notes

* 参考：
  * `yudao-module-system/yudao-module-system-server/src/main/java/cn/iocoder/yudao/module/system/framework/security/config/SecurityConfiguration.java`
  * `yudao-module-infra/yudao-module-infra-server/src/main/java/cn/iocoder/yudao/module/infra/framework/security/config/SecurityConfiguration.java`
  * `yudao-framework/yudao-spring-boot-starter-security/src/main/java/cn/iocoder/yudao/framework/security/config/YudaoWebSecurityConfigurerAdapter.java`
