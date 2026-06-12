# 前端实现需求：离线镜像包导出与下载（DevOps 流水线）

## 背景

后端已完成"离线镜像包交付"功能的公网打包侧。流水线新增了一种 Jenkins 节点 `EXPORT_OFFLINE_IMAGE`（导出离线镜像）：构建产物 `docker save` 成 tar 后由 Jenkins 上传到阿里云 OSS，平台记录离线包元数据，用户从平台下载 tar 包搬运到内网。

前端需要做两件事：**(1) 流水线编辑器支持新节点（大概率零代码，确认即可）；(2) 离线镜像包列表页 + 下载入口（主要工作量）。**

---

## 一、流水线编辑器：新增"导出离线镜像"节点

### 关键信息
节点已在后端注册，并通过现有接口 `GET /admin-api/devops/pipeline/node-types`（及 `/configurable-node-types`）下发。节点定义：

```
type:     "EXPORT_OFFLINE_IMAGE"
name:     "导出离线镜像"
category: "JENKINS"
icon:     "download"
enabled:  true
```

参数 schema（JSON Schema 风格，`paramSchema` 字段，前端应已有自动渲染逻辑）：

| 参数 key | 类型 | 必填 | 标签 | 默认值 |
|---|---|---|---|---|
| `imageName` | string | ✅ | 镜像名称 | `${APP_KEY}` |
| `imageTag` | string | ✅ | 镜像标签 | `${COMMIT_SHA}` |
| `ossEndpoint` | string | ✅ | OSS Endpoint | 空 |
| `ossBucket` | string | ✅ | OSS Bucket | 空 |
| `ossPath` | string | ✅ | OSS 路径前缀 | `offline-images/${APP_KEY}/` |
| `ossCredentialsId` | string | ✅ | Jenkins OSS 凭据 ID | 空 |

> schema 里还会带通用字段（`agentLabel`、`toolJdk`、`toolMaven`、`env`），与其它 JENKINS 节点一致，沿用现有渲染即可。

> ⚠️ **变更提醒（2026-06-12）**：原方案曾有 `ossUploadTool`（`aws-cli`/`ossutil` 下拉框）参数，现已删除——上传统一改用 Jenkins 的 Aliyun OSS Uploader 插件。若节点配置表单是从 `node-types` 接口动态渲染的，该下拉框会自动消失，无需改动；若曾硬编码该字段，请移除。

### 要做的事
1. **确认节点面板会自动列出该节点**——只要节点面板是从 `node-types` 接口动态渲染的，新节点会自动出现，可拖拽，配置表单自动按 `paramSchema` 渲染。**这种情况下无需写代码，只需验证一遍。**
2. 如果节点列表是**前端硬编码**的，则需要把 `EXPORT_OFFLINE_IMAGE`（图标 `download`、分类 JENKINS）加入节点清单。
3. 确认 `icon: "download"` 在前端图标库里有对应图标，没有就映射一个合适的下载类图标。

---

## 二、离线镜像包列表页 +下载入口（主要工作量）

### 2.1 新增 API（后端已实现，前端封装 service + 页面）

基础路径：`/admin-api/devops/offline-image-package`，权限码统一为 `devops:pipeline:query`。

#### ① 分页查询
```
GET /admin-api/devops/offline-image-package/page
```
**Query 参数：**
| 参数 | 类型 | 说明 |
|---|---|---|
| `pageNo` | int | 页码，从 1 开始 |
| `pageSize` | int | 每页条数 |
| `pipelineRunId` | long? | 按流水线运行编号过滤（可选） |
| `imageName` | string? | 镜像名称模糊匹配（可选） |
| `status` | int? | 状态过滤（可选）：`0`=打包中 `1`=就绪 `2`=失败 |
| `createTime` | string[]? | 创建时间范围 `[开始, 结束]`，格式 `yyyy-MM-dd HH:mm:ss` |

**返回 `CommonResult<PageResult<OfflineImagePackageRespVO>>`**

#### ② 详情
```
GET /admin-api/devops/offline-image-package/{id}
```
返回 `CommonResult<OfflineImagePackageRespVO>`

#### ③ 获取下载地址
```
GET /admin-api/devops/offline-image-package/{id}/download-url
```
返回 `CommonResult<string>`，data 是一个可直接访问的 OSS URL（已是公共可读地址）。

### 2.2 数据结构 `OfflineImagePackageRespVO`

```typescript
interface OfflineImagePackageRespVO {
  id: number              // 离线镜像包编号
  pipelineRunId: number   // 流水线运行编号
  imageName: string       // 镜像名称，如 registry.example.com/gone-server
  imageTag: string        // 镜像标签，如 v1.0.0
  imageDigest?: string    // 镜像摘要 SHA256，如 sha256:abc...（可能为空）
  architecture?: string   // 目标架构，如 amd64
  ossUrl?: string         // 离线包 OSS 访问地址（可能为空，未就绪时）
  packageSize?: number    // 包大小（字节）
  status: number          // 0=打包中 1=就绪 2=失败
  errorMessage?: string   // 错误信息（status=2 时有值）
  createTime: string      // 创建时间
  updateTime: string      // 更新时间
}
```

### 2.3 列表页要求

新建菜单/页面（建议路径 `views/devops/offlineImage/index.vue`，菜单挂在 DevOps 流水线下，参考现有 deployment/pipeline 列表页风格）：

**搜索栏：** 镜像名称（输入框，模糊）、状态（下拉：全部/打包中/就绪/失败）、创建时间（日期范围）、流水线运行编号（输入框，可选）。

**表格列：**
| 列 | 说明 |
|---|---|
| 镜像名称 | `imageName` |
| 标签 | `imageTag` |
| 架构 | `architecture` |
| 包大小 | `packageSize` 字节，**格式化为人类可读**（如 `500.2 MB`） |
| 状态 | `status` 用 tag 渲染：`0`→蓝色"打包中"、`1`→绿色"就绪"、`2`→红色"失败"（失败时 hover/展开显示 `errorMessage`） |
| 镜像摘要 | `imageDigest`，过长可省略号 + 复制按钮 |
| 流水线运行 | `pipelineRunId`，可做成链接跳转到对应流水线运行详情 |
| 创建时间 | `createTime` |
| 操作 | "下载"按钮（见下方下载逻辑） |

**下载按钮逻辑：**
- 仅当 `status === 1`（就绪）时可点击，其它状态置灰/隐藏。
- 点击时调用 `GET /{id}/download-url` 拿到 URL，然后**用该 URL 触发浏览器下载**（`window.open(url)` 或创建 `<a href download>` 触发）。
- 注意：返回的是 OSS 直链，文件可能较大（几百 MB ~ 几 GB），让浏览器原生下载即可，不要用 ajax 把 blob 读进内存。

### 2.4 流水线运行详情页：下载入口（次要）

流水线运行详情页已有节点日志列表，接口 `GET /admin-api/devops/pipeline-run/{runId}/logs`，返回的每个节点日志含：
```typescript
{ nodeId, nodeType, nodeName, status, ... }  // status: PENDING/RUNNING/SUCCESS/FAILED/...
```

**要求：** 当某节点 `nodeType === "EXPORT_OFFLINE_IMAGE"` 且 `status === "SUCCESS"` 时，在该节点上展示一个"下载离线包"入口。

> ⚠️ 注意：节点日志里**没有直接给出离线包 id**。获取离线包的方式：用该运行的 `pipelineRunId` 调分页接口 `GET /offline-image-package/page?pipelineRunId={runId}` 查出该运行下的离线包列表（通常一个导出节点对应一条记录），拿到 `id` 后再走 2.3 的下载逻辑。
>
> 如果一次运行有多个导出节点/多条记录，按 `imageName:imageTag` 匹配或直接列出让用户选。MVP 阶段可简化为：查到列表后，`status===1` 的逐条给下载按钮。

---

## 三、验收标准

- [ ] 流水线编辑器能拖拽"导出离线镜像"节点，配置表单能填上述 7 个参数。
- [ ] 离线镜像包列表页能分页查询，搜索条件生效，状态 tag 颜色正确，包大小格式化正确。
- [ ] 就绪状态的离线包点击"下载"能从 OSS 直链下载到 tar 文件。
- [ ] 打包中/失败状态的离线包不可下载，失败时能看到错误信息。
- [ ] 流水线运行详情页，导出节点成功后能找到下载入口并下载。

## 四、注意事项

- 这是**纯前端 Vue3 仓库**的工作，后端接口已全部就绪并通过编译。
- 权限码统一 `devops:pipeline:query`，需在菜单/按钮权限里配置（如系统用 RBAC 菜单管理，需新增对应菜单项和权限）。
- `ossUrl` / `download-url` 返回的是阿里云 OSS 公共读直链，前端直接用即可，无需再签名。
- 包大小格式化、digest 复制等可复用项目已有的工具函数/组件。
