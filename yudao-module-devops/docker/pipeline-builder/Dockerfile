FROM alibaba-cloud-linux-3-registry.cn-hangzhou.cr.aliyuncs.com/alinux3/alinux3:220901.1

ARG MAVEN_VERSION=3.9.9
ARG MAVEN_SHA512=a555254d6b53d267965a3404ecb14e53c3827c09c3b94b5678835887ab404556bfaf78dcfe03ba76fa2508649dca8531c74bca4d5846513522404d48e8c4ac8b
ARG NODE_VERSION=24.16.0

ENV LANG=C.UTF-8 \
    TZ=Asia/Shanghai \
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
    MAVEN_HOME=/opt/maven \
    M2_HOME=/opt/maven \
    NODE_HOME=/usr/local \
    PATH=/opt/maven/bin:/usr/local/bin:/usr/lib/jvm/java-17-openjdk/bin:$PATH

RUN dnf install -y --setopt=install_weak_deps=False \
        bash \
        ca-certificates \
        curl \
        findutils \
        git \
        gzip \
        java-17-openjdk-devel \
        procps-ng \
        tar \
        unzip \
        which \
        xz \
    && dnf clean all \
    && rm -rf /var/cache/dnf

RUN set -eux; \
    arch="$(uname -m)"; \
    case "${arch}" in \
        x86_64) node_arch="x64" ;; \
        aarch64) node_arch="arm64" ;; \
        *) echo "Unsupported architecture: ${arch}" >&2; exit 1 ;; \
    esac; \
    node_dist="node-v${NODE_VERSION}-linux-${node_arch}"; \
    curl -fsSLO "https://nodejs.org/dist/v${NODE_VERSION}/${node_dist}.tar.xz"; \
    curl -fsSLO "https://nodejs.org/dist/v${NODE_VERSION}/SHASUMS256.txt"; \
    grep " ${node_dist}.tar.xz\$" SHASUMS256.txt | sha256sum -c -; \
    tar -xJf "${node_dist}.tar.xz" -C /usr/local --strip-components=1; \
    rm -f "${node_dist}.tar.xz" SHASUMS256.txt; \
    corepack enable

RUN set -eux; \
    curl -fsSLo /tmp/apache-maven.tar.gz "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"; \
    echo "${MAVEN_SHA512}  /tmp/apache-maven.tar.gz" | sha512sum -c -; \
    mkdir -p /opt; \
    tar -xzf /tmp/apache-maven.tar.gz -C /opt; \
    ln -sfn "/opt/apache-maven-${MAVEN_VERSION}" /opt/maven; \
    rm -f /tmp/apache-maven.tar.gz

RUN mkdir -p /workspace /root/.m2/repository

WORKDIR /workspace

CMD ["sh", "-lc", "while true; do sleep 30; done"]
