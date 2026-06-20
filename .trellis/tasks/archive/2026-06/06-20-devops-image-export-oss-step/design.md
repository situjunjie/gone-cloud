# DevOps image export OSS pipeline step design

## Architecture

The new step should be a job-runtime step:

```text
PipelineExecutionEngine
  -> create job Docker runtime from runsOn.container
  -> DockerImageExportOssStepHandler
  -> docker exec inside job container
  -> /usr/local/bin/export-image-to-oss
  -> skopeo copy docker://... docker-archive:/workspace/...
  -> ossutil cp /workspace/... oss://bucket/path/file
```

This keeps the step consistent with the current `Command` runtime model while avoiding Docker-in-Docker. The container does not run `dockerd`, does not mount `/var/run/docker.sock`, and does not call `docker pull` or `docker save`.

## Docker Image

Recommended location:

```text
yudao-module-devops/docker/image-export-oss/
  Dockerfile
  README.md
  export-image-to-oss.sh
```

Recommended image tag:

```text
gone-cloud/image-export-oss:skopeo-ossutil
```

Recommended Dockerfile shape:

```dockerfile
FROM alibaba-cloud-linux-3-registry.cn-hangzhou.cr.aliyuncs.com/alinux3/alinux3:220901.1

ARG ALINUX_MIRROR=https://mirrors.aliyun.com
ARG OSSUTIL_VERSION=2.1.1

ENV LANG=C.UTF-8 \
    TZ=Asia/Shanghai \
    PATH=/usr/local/bin:$PATH

RUN set -eux; \
    printf '[alinux3-os]\nname=alinux3-os\nbaseurl=%s/alinux/3/os/$basearch/\ngpgkey=%s/alinux/3/RPM-GPG-KEY-ALINUX-3\nenabled=1\ngpgcheck=1\n' "${ALINUX_MIRROR}" "${ALINUX_MIRROR}" > /etc/yum.repos.d/alinux3-os.repo; \
    printf '[alinux3-plus]\nname=alinux3-plus\nbaseurl=%s/alinux/3/plus/$basearch/\ngpgkey=%s/alinux/3/RPM-GPG-KEY-ALINUX-3\nenabled=1\ngpgcheck=1\n' "${ALINUX_MIRROR}" "${ALINUX_MIRROR}" > /etc/yum.repos.d/alinux3-plus.repo; \
    dnf install -y --setopt=install_weak_deps=False \
        bash ca-certificates curl jq skopeo tar gzip zstd \
    && dnf clean all \
    && rm -rf /var/cache/dnf

# Install official ossutil here with pinned version and checksum verification.
# COPY ossutil /usr/local/bin/ossutil

COPY export-image-to-oss.sh /usr/local/bin/export-image-to-oss
RUN chmod +x /usr/local/bin/export-image-to-oss \
    && mkdir -p /workspace/jobs

WORKDIR /workspace
CMD ["sh", "-lc", "while true; do sleep 30; done"]
```

The final implementation must pin the concrete `ossutil` version and verify the downloaded artifact before shipping the Dockerfile.

## Entry Script Contract

The handler should pass inputs through environment variables, avoiding command-line secrets:

```text
IMAGE_REF
ARCHIVE_FORMAT
OUTPUT_FILE
REGISTRY_USERNAME
REGISTRY_PASSWORD
REGISTRY_TLS_VERIFY
OSS_ENDPOINT
OSS_PATH
OSS_ACCESS_KEY_ID
OSS_ACCESS_KEY_SECRET
OSS_OVERWRITE
```

The execution layer resolves simple `${VAR}` placeholders in step `with` parameters before handlers consume them. Variables come from run metadata, initial application context, current stage/job/step metadata, and completed upstream step outputs. The resolver supports exact variable replacement only; it does not implement expressions, defaults, or functions.

Script behavior:

1. Validate required environment variables.
2. Create the output directory under `/workspace/jobs/{jobId}/artifacts/`.
3. Run `skopeo copy` from `docker://$IMAGE_REF` to either:
   - `docker-archive:$OUTPUT_FILE:$IMAGE_REF`
   - `oci-archive:$OUTPUT_FILE:$IMAGE_REF`
4. Optionally compress the generated archive when `COMPRESSION` is not `none`.
5. Configure OSS authentication without echoing secrets.
6. Refuse overwrite when `OSS_OVERWRITE=false` and object already exists.
7. Upload the archive to `$OSS_PATH`.
8. Print only non-sensitive progress and result metadata.

## YAML Contract

Recommended minimal YAML:

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
            timeoutSeconds: 1800
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

Parameter contract:

| Field | Required | Default | Notes |
|---|---:|---|---|
| `image` | Yes | none | Full source image reference, including registry, repository, and tag or digest. |
| `archiveFormat` | No | `docker-archive` | First version supports `docker-archive` and `oci-archive`. |
| `compression` | No | `none` | Supported values: `none`, `gzip`, `zstd`. Compression wraps the exported archive file; consumers must decompress before loading/importing. |
| `outputFileName` | No | derived from image | File name under `jobs/{jobId}/artifacts/`; must not contain path traversal. |
| `registryCertificate.type` | Yes | `usernamePassword` | First version only supports `usernamePassword`. |
| `registryCertificate.username` | Yes | none | Source registry username. |
| `registryCertificate.password` | Yes | none | Sensitive. Must not be logged or stored in result JSON. |
| `registryTlsVerify` | No | `true` | Optional boolean for self-signed/internal registries. |
| `oss.endpoint` | Yes | none | OSS endpoint without scheme unless `ossutil` requires otherwise. |
| `oss.path` | Yes | none | Target OSS path in `ossutil` style, for example `oss://release-bucket/images/demo-1.0.oci.tar`. |
| `oss.certificate.type` | Yes | `accessKey` | First version only supports access key. |
| `oss.certificate.accessKeyId` | Yes | none | Sensitive enough to avoid unnecessary output. |
| `oss.certificate.accessKeySecret` | Yes | none | Sensitive. Must not be logged or stored in result JSON. |
| `overwrite` | No | `false` | Refuse upload if object exists when false. |

Step outputs/result JSON should include only:

```json
{
  "image": "registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0",
  "archiveFormat": "oci-archive",
  "compression": "none",
  "outputFileName": "demo-1.0.oci.tar",
  "ossPath": "oss://release-bucket/images/demo-1.0.oci.tar"
}
```

## Backend Integration

Required backend changes:

- Add `TYPE_DOCKER_IMAGE_EXPORT_OSS = "DockerImageExportOss"` to `PipelineNodeRegistryServiceImpl`.
- Register the node as a `BUILD` category node with default params and schema.
- Add it to supported executable step types in `PipelineSpecValidationServiceImpl`.
- Add parameter validation for nested `registryCertificate` and `oss.certificate`.
- Implement `DockerImageExportOssStepHandler` as `StepRuntimeRequirement.JOB_RUNTIME`.
- Execute `/usr/local/bin/export-image-to-oss` inside the job runtime through `PipelineCommandExecutor`.
- Reuse `CommandStepHandler.runtimeKey(ctx)` to access the prepared job runtime.
- Never write secret values into logs or `resultJson`.
- Return outputs with `image`, `archiveFormat`, `compression`, `outputFileName`, and `ossPath`.
- Update `PIPELINE_YAML_SPEC.md` and image README.

## Trade-offs

- Job-runtime execution keeps the step portable and avoids direct platform Docker daemon access.
- Requiring a specific `runsOn.container` image makes operational setup explicit. The handler can validate parameters but cannot guarantee the image contains `skopeo` and `ossutil` until runtime.
- Fixed official `ossutil` usage reduces OSS upload implementation risk in the first version, at the cost of pinning and maintaining the tool binary in the step image.
- Supporting `oci-archive` preserves a standards-based export option. `docker-archive` remains the default for compatibility with Docker-oriented consumers.
- Compression should be opt-in. It reduces OSS storage and transfer size for large images, but a compressed wrapper is no longer directly loadable/importable until decompressed.
- Inline username/password and access keys match the current `PrivateRegistryDockerBuild` style, but service-connection IDs would be safer long term.

## Rollback

The backend step registration and handler can be reverted independently from the Docker image directory. Existing pipeline YAML remains unaffected because the new step type is additive.
