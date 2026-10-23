FROM gone-cloud/pipeline-builder:java17-node24-maven3.9

# pipeline-builder 未安装 ssh 客户端，SSH 部署步骤需要 openssh-clients。
# alinux3 的 dnf 源已在基础镜像的 /etc/yum.repos.d 中配置好。
RUN set -eux; \
    dnf install -y \
        --setopt=install_weak_deps=False \
        --setopt=timeout=60 \
        --setopt=retries=10 \
        openssh-clients \
    && dnf clean all \
    && rm -rf /var/cache/dnf

COPY deploy-compose-service /usr/local/bin/deploy-compose-service
COPY deploy-compose-service-remote /usr/local/bin/deploy-compose-service-remote
RUN chmod +x /usr/local/bin/deploy-compose-service /usr/local/bin/deploy-compose-service-remote

WORKDIR /workspace
