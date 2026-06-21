# Pipeline Builder Image

DevOps 流水线 `local-docker/default` 执行池使用的通用构建镜像，基于 Alibaba Cloud Linux 3，预置：

- JDK 17
- Apache Maven 3.9.9
- Node.js 24.16.0、npm、corepack
- git、curl、tar、gzip、xz、unzip、findutils、procps 等常用构建工具
- skopeo、zstd、jq 等镜像归档导入/导出辅助工具
- `/usr/local/bin/import-image-archive-to-registry`：把 `DockerImageExportOss` 兼容镜像包导入目标 registry

## Build

```bash
docker build -t gone-cloud/pipeline-builder:java17-node24-maven3.9 \
  yudao-module-devops/docker/pipeline-builder
```

如需调整版本：

```bash
docker build \
  --build-arg NODE_VERSION=24.16.0 \
  --build-arg MAVEN_VERSION=3.9.9 \
  --build-arg MAVEN_SHA512=a555254d6b53d267965a3404ecb14e53c3827c09c3b94b5678835887ab404556bfaf78dcfe03ba76fa2508649dca8531c74bca4d5846513522404d48e8c4ac8b \
  -t gone-cloud/pipeline-builder:java17-node24-maven3.9 \
  yudao-module-devops/docker/pipeline-builder
```

## Verify

```bash
docker run --rm gone-cloud/pipeline-builder:java17-node24-maven3.9 sh -lc \
  'java -version && javac -version && mvn -version && node -v && npm -v && corepack --version && git --version && skopeo --version && zstd --version'
```

## Pipeline YAML

```yaml
runsOn:
  group: local-docker/default
  container: gone-cloud/pipeline-builder:java17-node24-maven3.9
```

容器默认工作目录为 `/workspace`，与后端 `DockerPipelineCommandExecutor` 的执行目录保持一致。
