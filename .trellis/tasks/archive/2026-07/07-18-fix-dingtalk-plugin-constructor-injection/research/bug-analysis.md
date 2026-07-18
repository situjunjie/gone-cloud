# Bug Analysis: DingTalkPlugin Spring 构造函数注入失败

## 1. Root Cause Category

- **Category**: D - Test Coverage Gap；E - Implicit Assumption
- **Specific Cause**: `DingTalkPlugin` 增加测试专用 `Clock` 构造函数后成为多构造函数组件，但生产构造函数没有显式 `@Autowired`。纯单元测试直接 `new` 插件，未经过 Spring 的构造函数选择流程。

## 2. Why Fixes Failed

- 首次功能实现的编译和纯单元测试均通过，但这些检查只能证明 Java 构造调用和插件逻辑正确，不能证明 Spring Bean 可实例化。

## 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|---|---|---|---|
| P0 | Architecture | 多构造函数 Spring 组件显式标注生产注入构造函数 | DONE |
| P0 | Test Coverage | 用 `AnnotationConfigApplicationContext` 创建插件和注册表 | DONE |
| P1 | Documentation | 将多构造函数组件规则写入后端质量规范 | DONE |

## 4. Systematic Expansion

- **Similar Issues**: 任何为 `Clock`、Executor 或测试替身增加重载构造函数的 `@Component/@Service` 都可能出现同类问题。
- **Design Improvement**: 保持生产构造函数唯一；确需测试重载时显式声明注入入口。
- **Process Improvement**: 新增 Spring 组件或改变构造函数集合时，至少执行一次轻量容器装配测试。

## 5. Knowledge Capture

- [x] 更新 `.trellis/spec/backend/quality-guidelines.md`。
- [x] 增加 DingTalk 插件及注册表的 Spring 容器回归测试。
