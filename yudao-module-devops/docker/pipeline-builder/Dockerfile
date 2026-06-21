FROM alibaba-cloud-linux-3-registry.cn-hangzhou.cr.aliyuncs.com/alinux3/alinux3:220901.1

ARG ALINUX_MIRROR=https://mirrors.aliyun.com
ARG MC_RELEASE=RELEASE.2025-07-21T05-28-08Z
ARG MC_AMD64_SHA256=ea4a453be116071ab1ccbd24eb8755bf0579649f41a7b94ab9e68571bb9f4a1e
ARG MC_ARM64_SHA256=62a7d4cc0ea37e7a5b564e0991057d4fec2a563566511fabe4ee5c1568f5e00b
ARG MAVEN_VERSION=3.9.9
ARG MAVEN_SHA512=a555254d6b53d267965a3404ecb14e53c3827c09c3b94b5678835887ab404556bfaf78dcfe03ba76fa2508649dca8531c74bca4d5846513522404d48e8c4ac8b
ARG MAVEN_DIST_BASE=https://archive.apache.org/dist/maven/maven-3
ARG NODE_VERSION=24.16.0
ARG NODE_DIST_BASE=https://npmmirror.com/mirrors/node

ENV LANG=C.UTF-8 \
    TZ=Asia/Shanghai \
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
    MAVEN_HOME=/opt/maven \
    M2_HOME=/opt/maven \
    NODE_HOME=/usr/local \
    PATH=/opt/maven/bin:/usr/local/bin:/usr/lib/jvm/java-17-openjdk/bin:$PATH

RUN set -eux; \
    printf '[alinux3-os]\nname=alinux3-os\nbaseurl=%s/alinux/3/os/$basearch/\ngpgkey=%s/alinux/3/RPM-GPG-KEY-ALINUX-3\nenabled=1\ngpgcheck=1\n' "${ALINUX_MIRROR}" "${ALINUX_MIRROR}" > /etc/yum.repos.d/alinux3-os.repo; \
    printf '[alinux3-plus]\nname=alinux3-plus\nbaseurl=%s/alinux/3/plus/$basearch/\ngpgkey=%s/alinux/3/RPM-GPG-KEY-ALINUX-3\nenabled=1\ngpgcheck=1\n' "${ALINUX_MIRROR}" "${ALINUX_MIRROR}" > /etc/yum.repos.d/alinux3-plus.repo; \
    printf '[alinux3-powertools]\nname=alinux3-powertools\nbaseurl=%s/alinux/3/powertools/$basearch/\ngpgkey=%s/alinux/3/RPM-GPG-KEY-ALINUX-3\nenabled=1\ngpgcheck=1\n' "${ALINUX_MIRROR}" "${ALINUX_MIRROR}" > /etc/yum.repos.d/alinux3-powertools.repo; \
    printf '[alinux3-updates]\nname=alinux3-updates\nbaseurl=%s/alinux/3/updates/$basearch/\ngpgkey=%s/alinux/3/RPM-GPG-KEY-ALINUX-3\nenabled=1\ngpgcheck=1\n' "${ALINUX_MIRROR}" "${ALINUX_MIRROR}" > /etc/yum.repos.d/alinux3-updates.repo; \
    printf '[alinux3-module]\nname=alinux3-module\nbaseurl=%s/alinux/3/module/$basearch/\ngpgkey=%s/alinux/3/RPM-GPG-KEY-ALINUX-3\nenabled=0\ngpgcheck=1\n' "${ALINUX_MIRROR}" "${ALINUX_MIRROR}" > /etc/yum.repos.d/alinux3-module.repo; \
    printf '[epel]\nname=Extra Packages for Enterprise Linux 8 - $basearch\nenabled=0\ngpgcheck=1\n' > /etc/yum.repos.d/epel.repo; \
    dnf install -y \
        --setopt=install_weak_deps=False \
        --setopt=timeout=60 \
        --setopt=retries=10 \
        bash \
        ca-certificates \
        curl \
        findutils \
        git \
        gzip \
        java-17-openjdk-devel \
        jq \
        procps-ng \
        skopeo \
        tar \
        unzip \
        which \
        xz \
        zstd \
    && dnf clean all \
    && rm -rf /var/cache/dnf

RUN set -eux; \
    arch="$(uname -m)"; \
    case "${arch}" in \
        x86_64) mc_arch="linux-amd64"; mc_sha256="${MC_AMD64_SHA256}" ;; \
        aarch64) mc_arch="linux-arm64"; mc_sha256="${MC_ARM64_SHA256}" ;; \
        *) echo "Unsupported architecture: ${arch}" >&2; exit 1 ;; \
    esac; \
    mc_url="https://dl.min.io/client/mc/release/${mc_arch}/archive/mc.${MC_RELEASE}"; \
    curl --http1.1 -fSL --retry 5 --retry-delay 2 --connect-timeout 20 --max-time 600 \
        -o /usr/local/bin/mc "${mc_url}"; \
    echo "${mc_sha256}  /usr/local/bin/mc" | sha256sum -c -; \
    chmod +x /usr/local/bin/mc; \
    mc --version

RUN set -eux; \
    arch="$(uname -m)"; \
    case "${arch}" in \
        x86_64) node_arch="x64" ;; \
        aarch64) node_arch="arm64" ;; \
        *) echo "Unsupported architecture: ${arch}" >&2; exit 1 ;; \
    esac; \
    node_dist="node-v${NODE_VERSION}-linux-${node_arch}"; \
    curl --http1.1 -fSL --retry 5 --retry-delay 2 --connect-timeout 20 --max-time 600 \
        -o "${node_dist}.tar.xz" "${NODE_DIST_BASE}/v${NODE_VERSION}/${node_dist}.tar.xz"; \
    curl --http1.1 -fSL --retry 5 --retry-delay 2 --connect-timeout 20 --max-time 120 \
        -o SHASUMS256.txt "${NODE_DIST_BASE}/v${NODE_VERSION}/SHASUMS256.txt"; \
    grep " ${node_dist}.tar.xz\$" SHASUMS256.txt | sha256sum -c -; \
    tar -xJf "${node_dist}.tar.xz" -C /usr/local --strip-components=1; \
    rm -f "${node_dist}.tar.xz" SHASUMS256.txt; \
    corepack enable

RUN set -eux; \
    curl --http1.1 -fSL --retry 5 --retry-delay 2 --connect-timeout 20 --max-time 600 \
        -o /tmp/apache-maven.tar.gz "${MAVEN_DIST_BASE}/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"; \
    echo "${MAVEN_SHA512}  /tmp/apache-maven.tar.gz" | sha512sum -c -; \
    mkdir -p /opt; \
    tar -xzf /tmp/apache-maven.tar.gz -C /opt; \
    ln -sfn "/opt/apache-maven-${MAVEN_VERSION}" /opt/maven; \
    rm -f /tmp/apache-maven.tar.gz

COPY import-image-archive-to-registry.sh /usr/local/bin/import-image-archive-to-registry
COPY export-image-to-object-storage.sh /usr/local/bin/export-image-to-object-storage
RUN chmod +x /usr/local/bin/import-image-archive-to-registry /usr/local/bin/export-image-to-object-storage \
    && mkdir -p /workspace /root/.m2/repository

WORKDIR /workspace

CMD ["sh", "-lc", "while true; do sleep 30; done"]
