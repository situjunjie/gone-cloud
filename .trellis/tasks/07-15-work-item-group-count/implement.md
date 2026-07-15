# Implementation Plan

1. [x] 补充请求/响应 VO，定义 `groupField`、动态 `value` 和 `count` API 合约。
2. [x] 为固定字段补充服务端物理列元数据，并增加内部 groupField 描述对象。
3. [x] 扩展 `ProjectWorkItemQueryConditionService`，统一校验并解析固定及自定义分组字段。
4. [x] 从 `ProjectWorkItemMapper.selectPage` 提取分页和统计共用的查询 Wrapper 构造逻辑，保持现有条件语义不变。
5. [x] 新增 Mapper 聚合方法和 XML SQL，支持固定字段、自定义标量字段、自定义单选/多选字段及 null 分组。
6. [x] 在 `ProjectWorkItemService` / `ProjectWorkItemServiceImpl` 增加分组统计方法并组装响应。
7. [x] 在 `ProjectWorkItemController` 增加 `POST /project/work-item/group-count`，复用查询权限。
8. [x] 增加条件解析、Wrapper 复用、固定字段、自定义字段、多选、空值和复杂条件组合测试。
9. [x] 运行聚焦测试：
   `mvn -pl yudao-module-project/yudao-module-project-server -am -Dtest='ProjectWorkItemQueryConditionServiceTest,ProjectWorkItemMapperTest,ProjectWorkItemServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test`
10. [x] 运行项目模块编译并检查残留重复查询逻辑和未受控动态 SQL 标识符。

## Validation Results

- 聚焦条件/Mapper/Service 测试通过。
- H2/MyBatis 集成测试实际执行聚合 XML，验证固定字段、自定义标量、选项、多选去重、null、排序和复杂条件。
- `mvn -pl yudao-module-project/yudao-module-project-server -am test` 通过，project server 共 42 个测试通过。
- 租户 SQL 改写测试和 `git diff --check` 通过。

## Review Gates

- 开发前确认 PRD 中多选分组、null 分组和原始值返回语义。
- Mapper 完成后先审查分页与统计是否真正共用条件构造，再进行 Service/Controller 接线。
- 完成前确认现有分页测试全部通过。

## Rollback Points

- 共用 Wrapper 重构后如果分页测试回归，先回滚该重构，不继续叠加聚合 SQL。
- 聚合接口无数据库迁移，可整体撤销新增 API/Mapper 文件而不处理数据。
