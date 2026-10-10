# DevOps Pipeline 构建镜像
# 包含git和常用构建工具，用于K8s构建集群

FROM ubuntu:22.04

LABEL maintainer="devops@gigimed.cn"
LABEL description="Pipeline builder image with git, Java, Node.js, and common build tools"

# 设置时区
ENV TZ=Asia/Shanghai \
    DEBIAN_FRONTEND=noninteractive

# 安装基础工具和git
RUN apt-get update && apt-get install -y \
    git \
    curl \
    wget \
    ca-certificates \
    gnupg \
    lsb-release \
    unzip \
    vim \
    jq \
    && rm -rf /var/lib/apt/lists/*

# 安装OpenJDK 17
RUN apt-get update && apt-get install -y \
    openjdk-17-jdk \
    && rm -rf /var/lib/apt/lists/*

# 安装Maven
ENV MAVEN_VERSION=3.9.6
RUN wget https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    && tar -xzf apache-maven-${MAVEN_VERSION}-bin.tar.gz -C /opt \
    && ln -s /opt/apache-maven-${MAVEN_VERSION} /opt/maven \
    && rm apache-maven-${MAVEN_VERSION}-bin.tar.gz

ENV MAVEN_HOME=/opt/maven
ENV PATH=${MAVEN_HOME}/bin:${PATH}

# 安装Node.js 20
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs \
    && npm install -g yarn pnpm \
    && rm -rf /var/lib/apt/lists/*

# 安装Docker CLI (用于docker命令，但不运行daemon)
RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
       | tee /etc/apt/sources.list.d/docker.list > /dev/null \
    && apt-get update \
    && apt-get install -y docker-ce-cli \
    && rm -rf /var/lib/apt/lists/*

# 安装kubectl (用于K8s部署)
RUN curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl" \
    && install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl \
    && rm kubectl

# 设置git配置
RUN git config --global core.autocrlf false \
    && git config --global core.filemode false \
    && git config --global credential.helper store

# 创建工作目录
RUN mkdir -p /workspace
WORKDIR /workspace

# 设置用户（非root）
RUN useradd -m -u 1000 -s /bin/bash builder \
    && chown -R builder:builder /workspace

USER builder

# 验证安装
RUN git --version \
    && java -version \
    && mvn --version \
    && node --version \
    && npm --version \
    && docker --version \
    && kubectl version --client

CMD ["/bin/bash"]
