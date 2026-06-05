def serviceMap = [
    'yudao-server': [module: 'yudao-server', dockerfile: 'yudao-server/Dockerfile', image: 'yudao-server'],
    'gateway-server': [module: 'yudao-gateway', dockerfile: 'yudao-gateway/Dockerfile', image: 'yudao-gateway'],
    'system-server': [module: 'yudao-module-system/yudao-module-system-server', dockerfile: 'yudao-module-system/yudao-module-system-server/Dockerfile', image: 'yudao-module-system-server'],
    'infra-server': [module: 'yudao-module-infra/yudao-module-infra-server', dockerfile: 'yudao-module-infra/yudao-module-infra-server/Dockerfile', image: 'yudao-module-infra-server'],
    'bpm-server': [module: 'yudao-module-bpm/yudao-module-bpm-server', dockerfile: 'yudao-module-bpm/yudao-module-bpm-server/Dockerfile', image: 'yudao-module-bpm-server']
]

pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'SERVICES', defaultValue: 'yudao-server', description: '要构建 Docker 镜像的服务模块，逗号分隔；填 all 表示全部已启用服务。可选：yudao-server,gateway-server,system-server,infra-server,bpm-server')
        string(name: 'DEPLOY_DIR', defaultValue: '/opt/gone-cloud/services', description: '服务器上的部署目录')
        string(name: 'IMAGE_REPO_PREFIX', defaultValue: 'gone-cloud', description: 'Docker 镜像仓库前缀')
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
                    def requested = params.SERVICES.split(',')
                            .collect { it.trim() }
                            .findAll { it }

                    if (requested.isEmpty()) {
                        error('SERVICES 不能为空')
                    }

                    def selected = requested.any { it == 'all' } ? serviceMap.keySet().toList() : requested
                    def unknown = selected.findAll { !serviceMap.containsKey(it) }
                    if (!unknown.isEmpty()) {
                        error("未知服务：${unknown.join(', ')}")
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
                    sh "mvn -pl ${env.SELECTED_MODULES} -am clean package ${mvnFlags}"
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
                    mkdir -p ${params.DEPLOY_DIR}
                    cp ${env.COMPOSE_DIR}/docker-compose.yml ${params.DEPLOY_DIR}/docker-compose.yml
                    cp ${env.COMPOSE_DIR}/README.md ${params.DEPLOY_DIR}/README.md
                    cp ${env.COMPOSE_DIR}/.env.example ${params.DEPLOY_DIR}/.env.example
                    if [ ! -f ${params.DEPLOY_DIR}/.env ]; then
                      echo '缺少外部部署配置：${params.DEPLOY_DIR}/.env'
                      echo '请参考 ${params.DEPLOY_DIR}/.env.example 创建 .env，并填写外部 MySQL、Redis、Nacos、XXL-Job 等地址后重试。'
                      exit 1
                    fi
                    if [ ! -s ${params.DEPLOY_DIR}/.env ]; then
                      echo '部署配置为空：${params.DEPLOY_DIR}/.env'
                      exit 1
                    fi
                    echo '使用外部部署配置：${params.DEPLOY_DIR}/.env'
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
                    sh """
                        set -eu
                        cd ${params.DEPLOY_DIR}

                        compose_services="\$(IMAGE_REPO_PREFIX=${params.IMAGE_REPO_PREFIX} IMAGE_TAG=${env.IMAGE_TAG} docker compose --env-file .env -f docker-compose.yml config --services)"
                        for service in ${servicesArg}; do
                          if ! printf '%s\\n' "\$compose_services" | grep -Fx "\$service" >/dev/null; then
                            echo "docker-compose.yml 中不存在服务：\$service"
                            exit 1
                          fi
                        done

                        IMAGE_REPO_PREFIX=${params.IMAGE_REPO_PREFIX} IMAGE_TAG=${env.IMAGE_TAG} docker compose \
                          --env-file .env \
                          -f docker-compose.yml \
                          config --quiet

                        IMAGE_REPO_PREFIX=${params.IMAGE_REPO_PREFIX} IMAGE_TAG=${env.IMAGE_TAG} docker compose \
                          --env-file .env \
                          -f docker-compose.yml \
                          up -d --remove-orphans ${servicesArg}

                        IMAGE_REPO_PREFIX=${params.IMAGE_REPO_PREFIX} IMAGE_TAG=${env.IMAGE_TAG} docker compose \
                          --env-file .env \
                          -f docker-compose.yml \
                          ps ${servicesArg}
                    """
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
