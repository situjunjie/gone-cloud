# Image Export OSS Runtime Image

DevOps 流水线 `DockerImageExportOss` step 使用的专用执行镜像。镜像内置：

- `skopeo`：从镜像仓库导出 `docker-archive` / `oci-archive`
- `ossutil`：上传归档文件到阿里云 OSS
- `gzip` / `zstd`：可选归档压缩
- `bash`、`curl`、`jq` 等基础工具

## Build

```bash
docker build -t gone-cloud/image-export-oss:skopeo-ossutil \
  yudao-module-devops/docker/image-export-oss
```

`ossutil` 版本和校验值已固定。需要升级时同时更新：

```bash
--build-arg OSSUTIL_VERSION=1.7.19
--build-arg OSSUTIL_AMD64_SHA256=dcc512e4a893e16bbee63bc769339d8e56b21744fd83c8212a9d8baf28767343
--build-arg OSSUTIL_ARM64_SHA256=f612c2a88d4d28363e254168d521fac5df632f2547ba84eaebacf6497dc04d57
```

Dockerfile 会根据 `uname -m` 自动下载 `linux-amd64` 或 `linux-arm64` 的官方包。

## Verify

```bash
docker run --rm gone-cloud/image-export-oss:skopeo-ossutil sh -lc \
  'skopeo --version && ossutil --version && gzip --version && zstd --version'
```

## Pipeline YAML

```yaml
stages:
  export_stage:
    name: 镜像导出
    jobs:
      export_job:
        name: 导出镜像并上传 OSS
        runsOn:
          group: local-docker/default
          container: gone-cloud/image-export-oss:skopeo-ossutil
        steps:
          export_image:
            name: 导出镜像到 OSS
            step: DockerImageExportOss
            with:
              image: registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0
              archiveFormat: oci-archive
              compression: none
              outputFileName: demo-1.0.oci.tar
              registryCertificate:
                type: usernamePassword
                username: <registry-username>
                password: <registry-password>
              oss:
                endpoint: oss-cn-hangzhou.aliyuncs.com
                path: oss://release-bucket/images/demo-1.0.oci.tar
                certificate:
                  type: accessKey
                  accessKeyId: <access-key-id>
                  accessKeySecret: <access-key-secret>
              overwrite: false
```

第一版不会对 `image`、`outputFileName`、`oss.path` 做 `${...}` 变量替换，参数会按配置原值执行。
