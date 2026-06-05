def serviceMap = [
    'gateway-server': [param: 'SERVICE_GATEWAY_SERVER', module: 'yudao-gateway', dockerfile: 'yudao-gateway/Dockerfile', image: 'yudao-gateway'],
    'system-server': [param: 'SERVICE_SYSTEM_SERVER', module: 'yudao-module-system/yudao-module-system-server', dockerfile: 'yudao-module-system/yudao-module-system-server/Dockerfile', image: 'yudao-module-system-server'],
    'infra-server': [param: 'SERVICE_INFRA_SERVER', module: 'yudao-module-infra/yudao-module-infra-server', dockerfile: 'yudao-module-infra/yudao-module-infra-server/Dockerfile', image: 'yudao-module-infra-server'],
    'bpm-server': [param: 'SERVICE_BPM_SERVER', module: 'yudao-module-bpm/yudao-module-bpm-server', dockerfile: 'yudao-module-bpm/yudao-module-bpm-server/Dockerfile', image: 'yudao-module-bpm-server']
]

pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        booleanParam(name: 'SELECT_ALL_SERVICES', defaultValue: false, description: '全选当前已启用服务')
        booleanParam(name: 'SERVICE_GATEWAY_SERVER', defaultValue: true, description: 'gateway-server')
        booleanParam(name: 'SERVICE_SYSTEM_SERVER', defaultValue: true, description: 'system-server')
        booleanParam(name: 'SERVICE_INFRA_SERVER', defaultValue: true, description: 'infra-server')
        booleanParam(name: 'SERVICE_BPM_SERVER', defaultValue: false, description: 'bpm-server')
        string(name: 'DEPLOY_DIR', defaultValue: '/data/situ/gone', description: '服务器上的部署目录')
        string(name: 'SSH_SERVER_NAME', defaultValue: '192.168.16.102', description: 'Jenkins Publish Over SSH 的 SSH Server Name')
        string(name: 'IMAGE_REPO_PREFIX', defaultValue: 'gone-cloud', description: 'Docker 镜像仓库前缀')
        string(name: 'MAVEN_TOOL_NAME', defaultValue: 'mvn3.9.9', description: 'Jenkins 全局 Maven 工具名称；留空则跳过 Jenkins tool')
        string(name: 'MAVEN_CMD', defaultValue: '', description: '备用 Maven 命令；Jenkins 全局 Maven 不可用时优先使用')
        string(name: 'MAVEN_DOCKER_IMAGE', defaultValue: 'maven:3.9.9-eclipse-temurin-17', description: '节点没有 mvn 时使用的 Maven Docker 镜像')
        booleanParam(name: 'SKIP_TESTS', defaultValue: true, description: '构建时是否跳过测试')
        booleanParam(name: 'DEPLOY_NOW', defaultValue: true, description: '镜像构建完成后是否立即部署所选服务；关闭后只构建 Jar 和 Docker 镜像')
    }

    environment {
        COMPOSE_DIR = 'script/docker/standalone'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Resolve Services') {
            steps {
                script {
                    def selected = params.SELECT_ALL_SERVICES
                            ? serviceMap.keySet().toList()
                            : serviceMap.findAll { serviceName, cfg -> params[cfg.param] }.keySet().toList()

                    if (selected.isEmpty()) {
                        error('请至少勾选一个服务，或勾选 SELECT_ALL_SERVICES 全选')
                    }

                    env.SELECTED_SERVICES = selected.unique().join(',')
                    env.SELECTED_MODULES = selected.collect { serviceMap[it].module }.unique().join(',')
                    env.IMAGE_TAG = env.BUILD_NUMBER

                    echo "Selected services: ${env.SELECTED_SERVICES}"
                    echo "Selected modules: ${env.SELECTED_MODULES}"
                }
            }
        }

        stage('Build Jar') {
            steps {
                script {
                    def mvnFlags = params.SKIP_TESTS ? '-DskipTests' : ''
                    def mvnArgs = "-pl ${env.SELECTED_MODULES} -am clean package ${mvnFlags}".trim()
                    def mavenToolName = params.MAVEN_TOOL_NAME?.trim()
                    def customMavenCmd = params.MAVEN_CMD?.trim()

                    def runFallbackMaven = {
                        if (customMavenCmd) {
                            sh "${customMavenCmd} ${mvnArgs}"
                            return
                        }
                        sh """
                            set -eu
                            if command -v mvn >/dev/null 2>&1; then
                              mvn ${mvnArgs}
                            else
                              if ! command -v docker >/dev/null 2>&1; then
                                echo 'Jenkins 节点未安装 mvn，也无法使用 docker run 执行 Maven。请安装 Maven 或 Docker，或设置 MAVEN_CMD。'
                                exit 1
                              fi
                              mkdir -p "\${HOME:-/tmp}/.m2/repository"
                              docker run --rm \
                                --user "\$(id -u):\$(id -g)" \
                                -v "\$PWD":/workspace \
                                -v "\${HOME:-/tmp}/.m2":/maven-cache \
                                -w /workspace \
                                ${params.MAVEN_DOCKER_IMAGE} \
                                mvn -Dmaven.repo.local=/maven-cache/repository ${mvnArgs}
                            fi
                        """
                    }

                    if (mavenToolName) {
                        def mavenHome = null
                        try {
                            mavenHome = tool name: mavenToolName, type: 'hudson.tasks.Maven$MavenInstallation'
                        } catch (err) {
                            echo "未找到 Jenkins 全局 Maven 工具：${mavenToolName}，继续使用备用 Maven 方式。"
                        }

                        if (mavenHome) {
                            withEnv(["PATH+MAVEN=${mavenHome}/bin"]) {
                                sh "mvn ${mvnArgs}"
                            }
                        } else {
                            runFallbackMaven()
                        }
                    } else {
                        runFallbackMaven()
                    }
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                    env.SELECTED_SERVICES.split(',').each { serviceName ->
                        def cfg = serviceMap[serviceName]
                        def imageRepo = "${params.IMAGE_REPO_PREFIX}/${cfg.image}"
                        sh """
                            docker build \
                              -t ${imageRepo}:${env.IMAGE_TAG} \
                              -t ${imageRepo}:latest \
                              -f ${cfg.dockerfile} \
                              ${cfg.module}
                        """
                    }
                }
            }
        }

        stage('Prepare Deploy Files') {
            when {
                expression { return params.DEPLOY_NOW }
            }
            steps {
                sh """
                    set -eu
                    rm -rf target/jenkins-deploy
                    mkdir -p target/jenkins-deploy
                    cp ${env.COMPOSE_DIR}/docker-compose.yml target/jenkins-deploy/docker-compose.yml
                    cp ${env.COMPOSE_DIR}/README.md target/jenkins-deploy/README.md
                    cp ${env.COMPOSE_DIR}/.env.example target/jenkins-deploy/env.example
                """
            }
        }

        stage('Deploy Selected Services') {
            when {
                expression { return params.DEPLOY_NOW }
            }
            steps {
                script {
                    def servicesArg = env.SELECTED_SERVICES.split(',').join(' ')
                    sshPublisher(publishers: [
                        sshPublisherDesc(
                            configName: params.SSH_SERVER_NAME,
                            transfers: [
                                sshTransfer(
                                    execCommand: "mkdir -p '${params.DEPLOY_DIR}'",
                                    execTimeout: 120000
                                ),
                                sshTransfer(
                                    cleanRemote: false,
                                    excludes: '',
                                    execCommand: """
                                        set -eu
                                        cd '${params.DEPLOY_DIR}'

                                        if [ -f env.example ]; then
                                          cp env.example .env.example
                                        fi
                                        if [ ! -f .env ]; then
                                          echo '缺少外部部署配置：${params.DEPLOY_DIR}/.env'
                                          echo '请参考 ${params.DEPLOY_DIR}/.env.example 创建 .env，并填写外部 MySQL、Redis、Nacos、XXL-Job 等地址后重试。'
                                          exit 1
                                        fi
                                        if [ ! -s .env ]; then
                                          echo '部署配置为空：${params.DEPLOY_DIR}/.env'
                                          exit 1
                                        fi
                                        echo '使用外部部署配置：${params.DEPLOY_DIR}/.env'

                                        compose_services="\$(IMAGE_REPO_PREFIX='${params.IMAGE_REPO_PREFIX}' IMAGE_TAG='${env.IMAGE_TAG}' docker compose --env-file .env -f docker-compose.yml config --services)"
                                        for service in ${servicesArg}; do
                                          if ! printf '%s\\n' "\$compose_services" | grep -Fx "\$service" >/dev/null; then
                                            echo "docker-compose.yml 中不存在服务：\$service"
                                            exit 1
                                          fi
                                        done

                                        IMAGE_REPO_PREFIX='${params.IMAGE_REPO_PREFIX}' IMAGE_TAG='${env.IMAGE_TAG}' docker compose \
                                          --env-file .env \
                                          -f docker-compose.yml \
                                          config --quiet

                                        IMAGE_REPO_PREFIX='${params.IMAGE_REPO_PREFIX}' IMAGE_TAG='${env.IMAGE_TAG}' docker compose \
                                          --env-file .env \
                                          -f docker-compose.yml \
                                          up -d --remove-orphans ${servicesArg}

                                        IMAGE_REPO_PREFIX='${params.IMAGE_REPO_PREFIX}' IMAGE_TAG='${env.IMAGE_TAG}' docker compose \
                                          --env-file .env \
                                          -f docker-compose.yml \
                                          ps ${servicesArg}
                                    """,
                                    execTimeout: 120000,
                                    flatten: false,
                                    makeEmptyDirs: false,
                                    noDefaultExcludes: false,
                                    patternSeparator: '[, ]+',
                                    remoteDirectory: params.DEPLOY_DIR,
                                    remoteDirectorySDF: false,
                                    removePrefix: 'target/jenkins-deploy',
                                    sourceFiles: 'target/jenkins-deploy/docker-compose.yml,target/jenkins-deploy/README.md,target/jenkins-deploy/env.example'
                                )
                            ],
                            usePromotionTimestamp: false,
                            useWorkspaceInPromotion: false,
                            verbose: true
                        )
                    ])
                }
            }
        }
    }

    post {
        success {
            echo "构建完成。服务：${env.SELECTED_SERVICES}，镜像标签：${env.IMAGE_TAG}"
        }
    }
}
