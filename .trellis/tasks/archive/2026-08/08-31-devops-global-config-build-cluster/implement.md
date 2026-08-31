# DevOps 全局配置类 - 实施计划

## 实施清单

- [x] 1. 在 DevOps server 配置包新增 `DevopsConfig`、`BuildConfig` 和只含 `environmentId` 的 `BuildClusterConfig` 嵌套结构。
- [x] 2. 添加非空默认嵌套对象和 Jackson 未知字段兼容声明。
- [x] 3. 增加聚焦测试，验证默认结构、未知字段兼容和 JSON 往返。
- [x] 4. 运行聚焦测试与 DevOps server 编译，确认没有 API、Service、SQL 或 Infra 模块改动。

## 验证命令

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am \
  -Dtest='DevopsConfigTest' -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile
```
