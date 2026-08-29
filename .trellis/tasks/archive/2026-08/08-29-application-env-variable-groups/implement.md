# 实现清单

1. 更新应用环境和变量组 DO、Save/Resp/Page VO 及转换映射。
2. 更新变量组 Mapper/Service 的查询、校验、唯一性和有效变量合并逻辑，增加 simple-list 服务契约。
3. 增加 simple-list 响应 VO、转换方法和控制器端点。
4. 更新 `sql/mysql/devops.sql` 的表结构及迁移注释/回填 SQL。
5. 搜索并修复所有 `getApplicationEnvId`、`applicationEnvId` 的变量组引用和编译错误。
6. 运行 `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`、相关测试及 `git diff --check`。

风险回滚点：Java 代码可独立回滚；数据库上线先回填新列，确认应用读取新关联后再删除旧列。
