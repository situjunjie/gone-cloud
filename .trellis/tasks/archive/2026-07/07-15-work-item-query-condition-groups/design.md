# Design

## Current State

`ProjectWorkItemPageReqVO.conditions` 是扁平列表。`ProjectWorkItemServiceImpl` 负责校验和解析每个条件，`ProjectWorkItemMapper` 顺序调用 `applyCondition`，MyBatis-Plus 因而将全部条件以 `AND` 连接。

查询视图使用 `condition_json` 和 `JacksonTypeHandler` 保存 `WorkItemQueryViewDO.QueryCondition`。该结构包含相同的扁平 `conditions`，但 `WorkItemQueryViewServiceImpl` 当前只校验视图编码和访问权限，不校验查询条件本身。

## API Contract

分页请求和查询视图条件新增相同的分组模型：

```json
{
  "projectId": 1,
  "conditionGroups": [
    {
      "conditions": [
        {"field": "custom:101", "operator": "eq", "value": "a"},
        {"field": "custom:103", "operator": "eq", "value": "c"}
      ]
    },
    {
      "conditions": [
        {"field": "custom:101", "operator": "eq", "value": "b"},
        {"field": "custom:102", "operator": "eq", "value": "a"}
      ]
    }
  ]
}
```

新增类型：

- `ProjectWorkItemPageReqVO.ConditionGroup`
  - `conditions: List<Condition>`
- `WorkItemQueryViewDO.ConditionGroup`
  - `conditions: List<FieldCondition>`

保留原有 `conditions`。当 `conditionGroups` 非空时使用新结构；当它为空时使用旧结构。两者同时非空直接拒绝。

## Validation And Normalization

将现有条件校验和解析从 `ProjectWorkItemServiceImpl` 中提取为模块内共享组件，例如 `ProjectWorkItemQueryConditionService`：

- 分页执行模式：校验并把请求副本规范化为 Mapper 可执行的字段枚举、自定义字段元数据和类型化值。
- 视图保存模式：复用同一字段/操作符/值/选项/项目边界规则，但不得修改将要持久化的原始条件，尤其不得把当前用户令牌替换为用户编号。

推荐实现方式是把查询视图条件转换为临时 `ProjectWorkItemPageReqVO`，对临时对象执行完整规范化；验证通过后仍保存原始 `QueryCondition`。这样分页执行只有一套权威规则，同时避免保存时令牌固化。

校验顺序：

1. 拒绝同时存在的扁平条件和条件组。
2. 校验每组非空，并统计全部条件不超过 20。
3. 对每个条件复用现有普通字段或 `custom:<fieldId>` 解析。
4. 自定义字段继续校验单一项目边界、字段作用域、字段类型和选项归属。
5. 规范化后的条件组交给 Mapper。

## SQL Construction

顶层固定过滤条件保持现状。仅当存在条件组时追加一个整体括号：

```text
fixed filters
AND (
  (group_1_condition_1 AND group_1_condition_2)
  OR
  (group_2_condition_1 AND group_2_condition_2)
)
```

Mapper 将现有 `applyCondition`、`applyCustomCondition` 等方法复用于组内 wrapper：

```java
query.and(groupsWrapper -> {
    for each group:
        groupsWrapper.or(groupWrapper -> {
            for each condition:
                applyCondition(groupWrapper, condition);
        });
});
```

实现时应避免首组生成多余的前导 `OR`，并通过生成 SQL 测试验证真实括号结构。扁平 `conditions` 继续走原有顺序 AND 路径，保持兼容。

自定义字段仍使用 `EXISTS` / `NOT EXISTS` 和绑定参数。条件组只改变主查询条件的布尔组合，不改变字段类型的单条件语义。

## Query View Persistence

数据库不变：`condition_json` 是 JSON 文本，新增 Java 属性后可直接序列化。

创建或更新视图时：

1. 复制或转换 `QueryCondition` 为临时分页请求。
2. 调用共享条件校验组件。
3. 校验成功后保存原始 `QueryCondition`。
4. 查询视图响应继续通过 MapStruct 返回完整 JSON 类型。

旧 JSON 没有 `conditionGroups` 时反序列化为 `null`，继续使用扁平 `conditions`。

## Error Handling

优先复用：

- `PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID`
- `PROJECT_WORK_ITEM_QUERY_FIELD_NOT_SUPPORTED`

结构错误的参数名应明确指向 `conditions` 或 `conditionGroups`。除非实现阶段确认现有错误码无法表达两种结构冲突，否则不新增错误码。

## Compatibility And Rollback

- 无数据库迁移。
- 旧分页请求和旧查询视图继续工作。
- 新客户端只提交 `conditionGroups`。
- 回滚 Java 代码后，数据库中已保存的 `conditionGroups` 会作为未知 JSON 属性被忽略或无法参与旧版本回放，因此部署回滚前应避免创建新格式视图；若需要强回滚能力，应先禁止新视图写入或清理新格式数据。

## Testing Strategy

- Service：结构冲突、空组、总数上限、普通/自定义字段、全局/项目边界、当前用户令牌。
- Mapper：两组 SQL 的括号、组内 AND、组间 OR、固定过滤 AND、自定义字段 `EXISTS`。
- Query view：新结构保存回读、旧结构兼容、保存时无效条件拒绝、用户令牌不被改写。
- Reactor：project server 聚焦测试、编译和全量模块测试。
