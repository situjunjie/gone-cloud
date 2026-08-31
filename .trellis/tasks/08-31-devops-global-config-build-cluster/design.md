# DevOps 全局配置类 - 技术设计

## 类结构

在 DevOps server 的配置包中新增 `DevopsConfig`：

```text
DevopsConfig
└── build: BuildConfig
    └── cluster: BuildClusterConfig
        └── environmentId: Long
```

对应 JSON：

```json
{
  "build": {
    "cluster": {
      "environmentId": 1024
    }
  }
}
```

使用 Lombok 生成访问方法；根类与嵌套类使用 Jackson `@JsonIgnoreProperties(ignoreUnknown = true)`。`build` 和 `cluster` 在字段声明处初始化，保证 `{}`、旧 JSON 或缺字段 JSON 得到稳定的非空结构。构建集群是平台全局引用，只保存 `environmentId`，不保存 `tenantId`。

本类只描述存入 `ConfigDO#value` 的 JSON 数据，不引用 `ConfigDO`，也不引入 Infra server 依赖。

## 验证

增加小型序列化测试，覆盖默认对象、未知字段和构建集群引用往返；运行 DevOps server 聚焦测试和编译。
