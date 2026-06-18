# Docker 镜像列表分页缓存

## Goal

优化 Docker 环境镜像列表接口，避免 `/devops/environment/docker/images` 一次返回过多镜像数据。后端提供内存分页、1 小时缓存，并支持前端刷新按钮通过请求参数绕过缓存重新拉取 Docker daemon 最新数据。

## Requirements

* Docker 镜像列表接口返回 `PageResult<EnvironmentDockerImageRespVO>`。
* 查询参数增加 `pageNo`、`pageSize`，默认使用项目 `PageParam` 语义。
* 查询参数增加 `refreshCache`，为 `true` 时强制重新从 Docker daemon 获取镜像和容器数据，并覆盖缓存。
* 缓存按环境维度保存原始 Docker 镜像列表和容器列表，TTL 为 1 小时。
* 分页在后端内存完成：先用现有 `keyword`、`dangling`、`unused` 过滤，再按创建时间倒序排序，最后截取当前页。
* 非 Docker 环境仍返回 `ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED`。

## Acceptance Criteria

* [ ] `/docker/images` 支持 `pageNo`、`pageSize`、`refreshCache` 参数。
* [ ] 默认查询命中 1 小时缓存，`refreshCache=true` 时重新查询 Docker daemon。
* [ ] 返回结构为 `PageResult`，包含当前页列表和过滤后的总数。
* [ ] targeted tests 和 devops-server compile 通过。

## Out of Scope

* Docker Engine 服务端分页。
* 分布式缓存。
* 镜像 pull/push/build/delete。
