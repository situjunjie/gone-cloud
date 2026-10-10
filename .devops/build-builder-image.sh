#!/bin/bash

# 构建并推送DevOps Pipeline构建镜像

set -e

# 配置
REGISTRY="registry.gigimed.cn"
IMAGE_NAME="devops/builder"
VERSION="1.0.0"
FULL_IMAGE="${REGISTRY}/${IMAGE_NAME}:${VERSION}"
LATEST_IMAGE="${REGISTRY}/${IMAGE_NAME}:latest"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKERFILE="${SCRIPT_DIR}/builder.Dockerfile"

echo "=== 构建 DevOps Pipeline 构建镜像 ==="
echo "镜像: ${FULL_IMAGE}"
echo "Dockerfile: ${DOCKERFILE}"
echo ""

# 构建镜像
echo ">>> 构建镜像..."
docker build -f "${DOCKERFILE}" -t "${FULL_IMAGE}" -t "${LATEST_IMAGE}" "${SCRIPT_DIR}/.."

echo ""
echo ">>> 镜像构建成功"
docker images | grep "${IMAGE_NAME}"

# 推送镜像
read -p "是否推送镜像到仓库? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo ">>> 推送镜像..."
    docker push "${FULL_IMAGE}"
    docker push "${LATEST_IMAGE}"
    echo ">>> 推送完成"
else
    echo ">>> 跳过推送"
fi

echo ""
echo "=== 完成 ==="
echo "镜像标签:"
echo "  - ${FULL_IMAGE}"
echo "  - ${LATEST_IMAGE}"
echo ""
echo "使用方法："
echo "  在 application-devops-k8s.yml 中配置："
echo "  devops.config.build.cluster.default-image: ${LATEST_IMAGE}"
