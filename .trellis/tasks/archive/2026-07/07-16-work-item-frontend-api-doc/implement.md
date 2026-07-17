# 工作项前端接口对接文档：实施计划

## 1. 契约核验

- [x] 核对七个接口的路由、HTTP 方法、权限、参数和返回模型。
- [x] 核对公共包络、分页、排序、Long 与时间序列化约定。
- [x] 核对固定字段、自定义字段、操作符、值解析和条件组合语义。
- [x] 核对 Schema 合并、创建/完整更新快照、详情回显和快捷更新 Patch 语义。
- [x] 核对分组来源、原始值类型、空值、多选计数和排序规则。

## 2. 文档编写

- [x] 新增 `docs/work-item-frontend-api.md`。
- [x] 编写公共约定与完整枚举表。
- [x] 为七个接口编写参数表、请求/响应示例和边界说明。
- [x] 编写固定字段与自定义字段操作符矩阵。
- [x] 编写前端推荐调用流程、显示值翻译和错误处理建议。
- [x] 编写可供前端直接实现的联调检查清单。

## 3. Swagger 对齐

- [x] 补充 `is_empty`、`is_not_empty` 操作符描述。
- [x] 补充 `self` 当前用户令牌描述。
- [x] 搜索其他文档与当前源码的明显残留差异。

## 4. 质量验证

- [x] 使用 `trellis-check` 检查文档与跨层契约一致性。
- [x] 运行 `mvn -pl yudao-module-project/yudao-module-project-server -am test`。
- [x] 运行接口/枚举残留搜索及 `git diff --check`。
- [x] 回读 Markdown，确认示例 JSON 合法且前端无需 Java 上下文。

## 5. 收尾

- [x] 判断是否需要更新 code-spec；本任务不改变现有契约，不重复已有 code-spec。
- [x] 提交文档与 Swagger 元数据修正。
- [x] 归档 Trellis 任务并记录开发日志。

## 6. 验证结果

- 增量全量测试：54 tests，0 failures，0 errors。
- clean 全量测试：54 tests，0 failures，0 errors；重新编译 76 个主源码和 9 个测试源码。
- Markdown JSON 示例：18 个代码块全部通过 `JSON.parse`。
- 接口、权限、枚举、操作符和错误码残留搜索：通过。
- `git diff --check`：通过。
