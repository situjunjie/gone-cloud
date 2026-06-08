# DevOps Jenkins Runner 配置指引

本文档用于配置 Gone DevOps 平台流水线的 Jenkins 执行器。当前阶段 Jenkins 对用户不可见，用户只在平台配置、触发、取消流水线；平台代码合并成功后由后端自动触发 Jenkins。

## 1. 整体链路

1. 平台完成代码合并。
2. 后端调用 Jenkins `buildWithParameters`。
3. Jenkins Runner Job 接收平台传入的 `JENKINSFILE_TEXT`、`PIPELINE_RUN_ID`、`CALLBACK_URL`、`CALLBACK_TOKEN` 等参数。
4. Runner Job 执行平台生成的 Jenkinsfile。
5. Jenkinsfile 每个 stage 前后调用 `goneDevopsCallback(...)`。
6. 平台收到 Jenkins 回调后更新 `dev_pipeline_run_log` 和流水线运行状态。

当前阶段支持 `CHECKOUT`、`MAVEN_BUILD_JAR`、`NPM_BUILD`、`DOCKER_BUILD_PUSH`、`ARTIFACT_UPLOAD` 和 `MOCK` 节点链路。

## 2. Jenkins 插件

建议确认 Jenkins 已安装以下插件：

- Pipeline
- Pipeline: Groovy
- HTTP Request Plugin
- Credentials Binding
- Git plugin
- Docker Pipeline
- Pipeline Utility Steps（推荐，用于后续扩展）
- NodeJS Plugin（如果希望通过 Jenkins 工具配置管理 Node 版本）
- Config File Provider（如果 Maven settings 由 Jenkins 托管）

Jenkins Agent 需要具备对应命令运行环境：Git、Maven、Node/npm、Docker CLI，并能访问 Docker daemon 和镜像仓库。

## 3. 配置 Shared Library

后端生成的 Jenkinsfile 固定引用：

```groovy
@Library('gone-devops-shared') _
```

因此 Jenkins 必须配置一个全局共享库，名称必须为：

```text
gone-devops-shared
```

配置路径：

```text
Manage Jenkins -> System -> Global Trusted Pipeline Libraries
```

推荐配置：

- Name: `gone-devops-shared`
- Default version: `master` 或 `main`
- Retrieval method: Modern SCM
- SCM: Git
- Project Repository: shared library 仓库地址
- 建议作为 trusted library 使用，便于回调方法处理 password 参数。

shared library 仓库结构：

```text
gone-devops-shared/
└── vars/
    ├── goneDevopsCallback.groovy
    ├── goneDevopsCheckout.groovy
    ├── goneDevopsMavenBuildJar.groovy
    ├── goneDevopsNpmBuild.groovy
    └── goneDevopsDockerBuildPush.groovy
```

`vars/goneDevopsCallback.groovy` 最小实现：

```groovy
import groovy.json.JsonOutput

def call(Map args = [:]) {
    String baseUrl = String.valueOf(args.callbackUrl ?: '').trim()
    if (baseUrl.endsWith('/')) {
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1)
    }

    String runId = String.valueOf(args.runId ?: '').trim()
    String token = secretToString(args.callbackToken)

    def payload = [
        action: args.action,
        pipelineVersionId: toLong(args.pipelineVersionId),
        nodeId: args.nodeId,
        nodeType: args.nodeType,
        nodeName: args.nodeName,
        jenkinsJobName: env.JOB_NAME,
        jenkinsBuildNumber: env.BUILD_NUMBER,
        jenkinsBuildUrl: env.BUILD_URL,
        commitSha: params.COMMIT_SHA,
        timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX"),
        message: args.message == null ? null : String.valueOf(args.message)
    ]

    httpRequest(
        httpMode: 'POST',
        url: "${baseUrl}/${runId}/jenkins/callback",
        contentType: 'APPLICATION_JSON',
        acceptType: 'APPLICATION_JSON',
        customHeaders: [[
            name: 'X-Devops-Callback-Token',
            value: token,
            maskValue: true
        ]],
        requestBody: JsonOutput.toJson(payload),
        validResponseCodes: '200:299'
    )
}

private String secretToString(Object value) {
    if (value == null) {
        return ''
    }
    if (value.metaClass.respondsTo(value, 'getPlainText')) {
        return value.getPlainText()
    }
    return String.valueOf(value)
}

private Long toLong(Object value) {
    if (value == null || String.valueOf(value).trim().isEmpty()) {
        return null
    }
    return Long.valueOf(String.valueOf(value))
}
```

### 3.1 真实节点 wrapper 最小实现

`vars/goneDevopsCheckout.groovy`：

```groovy
def call(Map args = [:]) {
    checkout([
        $class: 'GitSCM',
        branches: [[name: String.valueOf(args.branchName)]],
        userRemoteConfigs: [[url: String.valueOf(args.repoUrl)]]
    ])
    String commitSha = String.valueOf(args.commitSha ?: '').trim()
    if (commitSha) {
        sh "git checkout ${commitSha}"
    }
}
```

`vars/goneDevopsMavenBuildJar.groovy`：

```groovy
def call(Map args = [:]) {
    String workingDir = String.valueOf(args.workingDir ?: '.')
    String goals = String.valueOf(args.goals ?: 'clean package')
    String profiles = String.valueOf(args.profiles ?: '').trim()
    boolean skipTests = Boolean.valueOf(String.valueOf(args.skipTests ?: 'true'))
    String mavenOptions = String.valueOf(args.mavenOptions ?: '').trim()
    dir(workingDir) {
        withEnv(mavenOptions ? ["MAVEN_OPTS=${mavenOptions}"] : []) {
            String command = "mvn ${goals}"
            if (profiles) {
                command += " -P${profiles}"
            }
            if (skipTests) {
                command += " -DskipTests"
            }
            sh command
        }
    }
}
```

`vars/goneDevopsNpmBuild.groovy`：

```groovy
def call(Map args = [:]) {
    String workingDir = String.valueOf(args.workingDir ?: '.')
    String installCommand = String.valueOf(args.installCommand ?: 'npm ci')
    String buildCommand = String.valueOf(args.buildCommand ?: 'npm run build')
    dir(workingDir) {
        sh installCommand
        sh buildCommand
    }
}
```

`vars/goneDevopsDockerBuildPush.groovy`：

```groovy
def call(Map args = [:]) {
    String imageName = resolveExpression(String.valueOf(args.imageName ?: params.APP_KEY))
    String imageTag = resolveExpression(String.valueOf(args.imageTag ?: (params.COMMIT_SHA ?: env.BUILD_NUMBER)))
    String dockerfile = String.valueOf(args.dockerfile ?: 'Dockerfile')
    String context = String.valueOf(args.context ?: '.')
    String registryUrl = String.valueOf(args.registryUrl ?: '').trim()
    String credentialsId = String.valueOf(args.credentialsId ?: '').trim()
    boolean push = Boolean.valueOf(String.valueOf(args.push ?: 'true'))
    boolean pushLatest = Boolean.valueOf(String.valueOf(args.pushLatest ?: 'false'))

    String fullImage = registryUrl ? "${registryUrl.replaceFirst('/$', '')}/${imageName}:${imageTag}" : "${imageName}:${imageTag}"
    def image = docker.build(fullImage, "-f ${dockerfile} ${context}")
    if (push) {
        if (registryUrl) {
            docker.withRegistry(registryUrl, credentialsId) {
                image.push()
                if (pushLatest) {
                    image.push('latest')
                }
            }
        } else {
            image.push()
            if (pushLatest) {
                image.push('latest')
            }
        }
    }
    return imageTag
}

private String resolveExpression(String value) {
    return value
        .replace('${APP_KEY}', String.valueOf(params.APP_KEY ?: ''))
        .replace('${COMMIT_SHA}', String.valueOf(params.COMMIT_SHA ?: env.BUILD_NUMBER))
        .replace('${BUILD_NUMBER}', String.valueOf(env.BUILD_NUMBER ?: ''))
}
```

`ARTIFACT_UPLOAD` 节点使用 Jenkins 原生 `archiveArtifacts`，不需要额外 shared library 方法。

## 4. 创建 Jenkins Runner Job

创建一个 Pipeline Job，建议命名为：

```text
gone-devops-runner
```

如果 Job 放在 Jenkins 文件夹里，例如：

```text
gone/devops-runner
```

则后端 `devops.jenkins.job-name` 需要配置为：

```text
gone/devops-runner
```

### 4.1 Job 参数

勾选 `This project is parameterized`，新增以下参数：

| 参数名 | 类型 | 说明 |
| --- | --- | --- |
| `PIPELINE_RUN_ID` | String Parameter | 平台流水线运行编号 |
| `PIPELINE_VERSION_ID` | String Parameter | 平台流水线版本编号 |
| `REPO_URL` | String Parameter | 仓库地址 |
| `BRANCH_NAME` | String Parameter | 平台合并后的部署分支 |
| `COMMIT_SHA` | String Parameter | 部署分支提交 SHA |
| `APP_KEY` | String Parameter | 应用标识 |
| `CALLBACK_URL` | String Parameter | 平台回调基础地址 |
| `CALLBACK_TOKEN` | Password Parameter | Jenkins 回调平台的共享令牌 |
| `JENKINSFILE_TEXT` | Text Parameter | 平台生成的 Jenkinsfile 文本 |

注意：`JENKINSFILE_TEXT` 必须使用 Text Parameter，因为它是多行文本；`CALLBACK_TOKEN` 建议使用 Password Parameter。

### 4.2 Runner Job Pipeline 脚本

Runner Job 的 Pipeline 脚本使用：

```groovy
def jenkinsfileText = params.JENKINSFILE_TEXT

if (jenkinsfileText == null || jenkinsfileText.trim().isEmpty()) {
    error 'JENKINSFILE_TEXT is blank'
}

evaluate(jenkinsfileText)
```

如果 Jenkins 开启 Groovy sandbox，可能需要脚本审批。MVP 联调阶段建议将 Runner Job 或 shared library 配置为可信执行环境。

## 5. 后端配置

后端需要配置 Jenkins 集成参数。环境变量示例：

```env
DEVOPS_JENKINS_ENABLED=true
DEVOPS_JENKINS_BASE_URL=http://你的Jenkins地址:8080
DEVOPS_JENKINS_JOB_NAME=gone-devops-runner
DEVOPS_JENKINS_USERNAME=gone-devops
DEVOPS_JENKINS_API_TOKEN=你的Jenkins用户ApiToken
DEVOPS_JENKINS_CALLBACK_URL=http://你的平台网关地址/admin-api/devops/pipeline-run
DEVOPS_JENKINS_CALLBACK_TOKEN=一个足够长的随机密钥
```

结合常见 Docker 部署示例：

```env
DEVOPS_JENKINS_ENABLED=true
DEVOPS_JENKINS_BASE_URL=http://192.168.16.102:8080
DEVOPS_JENKINS_JOB_NAME=gone-devops-runner
DEVOPS_JENKINS_USERNAME=gone-devops
DEVOPS_JENKINS_API_TOKEN=xxxxxxxx
DEVOPS_JENKINS_CALLBACK_URL=http://192.168.16.102:52080/admin-api/devops/pipeline-run
DEVOPS_JENKINS_CALLBACK_TOKEN=gone-devops-callback-secret-please-change
```

如果 Jenkins 直接访问 DevOps 服务而不经过网关，`DEVOPS_JENKINS_CALLBACK_URL` 需要换成 DevOps 服务实际可访问地址，并确认是否仍需要 `/admin-api` 前缀。

## 6. Jenkins 用户权限

建议创建专用 Jenkins 用户：

```text
gone-devops
```

授予最小权限：

- Overall/Read
- Job/Read
- Job/Build
- Job/Cancel

后端取消平台流水线时会调用 Jenkins stop API，因此需要 `Job/Cancel` 权限。

API Token 创建路径：

```text
用户 -> Configure -> API Token -> Add new Token
```

创建后填入：

```env
DEVOPS_JENKINS_API_TOKEN=...
```

## 7. 网络连通性检查

从后端容器测试能否访问 Jenkins：

```bash
curl -i http://192.168.16.102:8080/login
```

从 Jenkins 容器测试能否访问平台 callback 地址：

```bash
curl -i http://192.168.16.102:52080/admin-api/devops/pipeline-run
```

该 GET 请求返回 404 或 405 都可以，关键是不能连接超时、DNS 不通或网络不可达。

## 8. 联调步骤

1. 在平台配置一条真实 Jenkins 流水线，例如：
   - `CHECKOUT -> MAVEN_BUILD_JAR -> DOCKER_BUILD_PUSH -> ARTIFACT_UPLOAD`
   - 或 `CHECKOUT -> NPM_BUILD -> ARTIFACT_UPLOAD`
2. 保存草稿并发布。
3. 在应用详情页提交变更发布。
4. 后端完成代码合并后，检查 Jenkins 是否启动 `gone-devops-runner`。
5. Jenkins 控制台应看到对应 stage 依次执行，并在平台生成节点日志。

仍可使用 `MOCK` 节点做最小链路验证，Jenkins 控制台应看到类似输出：

   ```text
   MOCK node: xxx
   ```

6. 平台 `dev_pipeline_run_log` 应出现 `MOCK` 节点日志。
7. 应用详情页当前运行状态应看到 `MOCK` 节点从 `RUNNING` 到 `SUCCESS`。

## 9. 常见问题

### Jenkins 没有启动

检查：

- `DEVOPS_JENKINS_ENABLED=true`
- `DEVOPS_JENKINS_BASE_URL`
- `DEVOPS_JENKINS_JOB_NAME`
- Jenkins 用户 API Token 是否正确
- Jenkins 用户是否有 `Job/Build` 权限

### Jenkins 启动了但回调失败

检查：

- `DEVOPS_JENKINS_CALLBACK_URL` 是否是 Jenkins 能访问的地址
- `CALLBACK_TOKEN` 是否和后端 `DEVOPS_JENKINS_CALLBACK_TOKEN` 一致
- Jenkins 是否安装 HTTP Request Plugin
- shared library 名称是否严格等于 `gone-devops-shared`
- 平台网关是否能访问 `/admin-api/devops/pipeline-run/{runId}/jenkins/callback`

### 报找不到 `goneDevopsCallback`

检查：

- shared library 是否配置成功
- shared library 名称是否为 `gone-devops-shared`
- 仓库结构是否为 `vars/goneDevopsCallback.groovy`
- Jenkins 是否成功拉取 shared library 仓库

### 报找不到真实节点 wrapper

检查 `gone-devops-shared/vars/` 下是否存在：

- `goneDevopsCheckout.groovy`
- `goneDevopsMavenBuildJar.groovy`
- `goneDevopsNpmBuild.groovy`
- `goneDevopsDockerBuildPush.groovy`

### Docker 构建或推送失败

检查：

- Jenkins Agent 是否安装 Docker CLI
- Jenkins Agent 用户是否能访问 Docker daemon
- `registryUrl`、`registryCredentialsId` 是否和 Jenkins 凭据配置一致
- 镜像仓库是否允许当前 Jenkins 网络访问

### Jenkinsfile 执行需要脚本审批

如果 Runner Job 使用 `evaluate(JENKINSFILE_TEXT)`，Jenkins 可能要求 Groovy 脚本审批。MVP 阶段建议使用 trusted library 或可信 Runner Job。生产阶段可进一步收敛 Runner 执行模型，降低动态脚本执行风险。

## 10. 当前 MVP 边界

- 前端不暴露 Jenkins 概念。
- 前端不提供启动 Jenkins 构建按钮。
- Jenkins 只作为平台流水线执行器。
- 制品上传本期使用 Jenkins 原生 `archiveArtifacts`。
- Nexus/MinIO 等外部制品库上传、审批、部署节点不在本期范围。
