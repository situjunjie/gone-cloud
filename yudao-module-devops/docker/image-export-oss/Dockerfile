FROM alibaba-cloud-linux-3-registry.cn-hangzhou.cr.aliyuncs.com/alinux3/alinux3:220901.1

ARG ALINUX_MIRROR=https://mirrors.aliyun.com
ARG OSSUTIL_VERSION=1.7.19
ARG OSSUTIL_AMD64_SHA256=dcc512e4a893e16bbee63bc769339d8e56b21744fd83c8212a9d8baf28767343
ARG OSSUTIL_ARM64_SHA256=f612c2a88d4d28363e254168d521fac5df632f2547ba84eaebacf6497dc04d57

ENV LANG=C.UTF-8 \
    TZ=Asia/Shanghai \
    PATH=/usr/local/bin:$PATH

RUN set -eux; \
    printf '[alinux3-os]\nname=alinux3-os\nbaseurl=%s/alinux/3/os/$basearch/\ngpgkey=%s/alinux/3/RPM-GPG-KEY-ALINUX-3\nenabled=1\ngpgcheck=1\n' "${ALINUX_MIRROR}" "${ALINUX_MIRROR}" > /etc/yum.repos.d/alinux3-os.repo; \
    printf '[alinux3-plus]\nname=alinux3-plus\nbaseurl=%s/alinux/3/plus/$basearch/\ngpgkey=%s/alinux/3/RPM-GPG-KEY-ALINUX-3\nenabled=1\ngpgcheck=1\n' "${ALINUX_MIRROR}" "${ALINUX_MIRROR}" > /etc/yum.repos.d/alinux3-plus.repo; \
    dnf install -y --setopt=install_weak_deps=False \
        bash \
        ca-certificates \
        curl \
        findutils \
        gzip \
        jq \
        skopeo \
        unzip \
        zstd \
    && dnf clean all \
    && rm -rf /var/cache/dnf

RUN set -eux; \
    arch="$(uname -m)"; \
    case "${arch}" in \
        x86_64) ossutil_arch="amd64"; ossutil_sha256="${OSSUTIL_AMD64_SHA256}" ;; \
        aarch64) ossutil_arch="arm64"; ossutil_sha256="${OSSUTIL_ARM64_SHA256}" ;; \
        *) echo "Unsupported architecture: ${arch}" >&2; exit 1 ;; \
    esac; \
    ossutil_url="https://gosspublic.alicdn.com/ossutil/${OSSUTIL_VERSION}/ossutil-v${OSSUTIL_VERSION}-linux-${ossutil_arch}.zip"; \
    curl --http1.1 -fSL --retry 5 --retry-delay 2 --connect-timeout 20 --max-time 600 \
        -o /tmp/ossutil.zip "${ossutil_url}"; \
    echo "${ossutil_sha256}  /tmp/ossutil.zip" | sha256sum -c -; \
    mkdir -p /tmp/ossutil; \
    unzip -q /tmp/ossutil.zip -d /tmp/ossutil; \
    find /tmp/ossutil -type f -name 'ossutil*' -perm /111 -exec cp {} /usr/local/bin/ossutil \; -quit; \
    chmod +x /usr/local/bin/ossutil; \
    ossutil --version; \
    rm -rf /tmp/ossutil /tmp/ossutil.zip

COPY export-image-to-oss.sh /usr/local/bin/export-image-to-oss
RUN chmod +x /usr/local/bin/export-image-to-oss \
    && mkdir -p /workspace/jobs

WORKDIR /workspace

CMD ["sh", "-lc", "while true; do sleep 30; done"]
