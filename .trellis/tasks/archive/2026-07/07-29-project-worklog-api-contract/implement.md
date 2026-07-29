# 实施清单

1. 在 `WorklogWorkItemScopeEnum` 增加 `SELF_AND_DESCENDANTS`，调整 `WorklogServiceImpl` 统一范围解析，并保留空范围短路和 CTE 循环安全。
2. 在工时汇总 Service 注入/复用工作项、用户、迭代的批量查询能力，按分组枚举填充 `displayName`，不改变 SQL 汇总口径。
3. 补充 `work-item-frontend-api.md`：新范围语义、ID 的动态无损类型规则、三种分组展示名和基于 `my-permissions` 的按钮判定规则。
4. 更新相关 DTO、Mapper、Service、Controller/安全与 Jackson 测试，覆盖范围、展示名、权限和 Long 序列化边界。
5. 运行 project 模块聚焦测试，随后运行项目模块完整测试；审查 `git diff`，确认无无关改动及无全仓 ID 类型漂移。

## Expected Files

- `.../enums/WorklogWorkItemScopeEnum.java`
- `.../service/worklog/WorklogServiceImpl.java`
- `.../controller/admin/worklog/vo/WorklogSummaryRespVO.java`（仅在装配辅助需要时调整）
- `.../docs/work-item-frontend-api.md`
- 对应 worklog、权限和 Jackson 测试

## Validation Commands

```bash
mvn -pl yudao-module-project/yudao-module-project-server test
mvn -pl yudao-module-project/yudao-module-project-server -am test
```

若全模块测试耗时或受环境依赖阻塞，先执行受影响测试类并报告准确命令与结果。
