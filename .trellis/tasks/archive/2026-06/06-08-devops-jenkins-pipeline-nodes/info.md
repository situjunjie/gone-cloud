# DevOps Jenkins-Compatible Pipeline Nodes Technical Design

## 1. Current State

The current backend already has the right high-level architecture:

- Platform owns pipeline DSL, versions, validation, and generated Jenkinsfile.
- Jenkins Runner Job receives `JENKINSFILE_TEXT` through `buildWithParameters`.
- Generated Jenkinsfile calls `goneDevopsCallback(...)` before and after each stage.
- Platform callback service updates `dev_pipeline_run_log` and marks the run successful when all DSL nodes complete.

The gap is node fidelity:

- Node registry exposes weak `paramSchema`.
- `BUILD_ARTIFACT` and `BUILD_IMAGE` are too generic for "Jenkins stage equivalent" configuration.
- Jenkinsfile generation still relies on command templates for build/test and hides Jenkins-native options.
- Frontend cannot know which Jenkins-equivalent parameters to render without a stronger backend schema.

## 2. Design Principles

1. Keep platform-owned DSL. Jenkins executes generated output only.
2. One enabled platform node equals one Jenkins `stage`.
3. Stage display name should match platform node name. Node id/type remain callback metadata.
4. Node params must map to Jenkins-native directives or steps, not arbitrary frontend shell.
5. Validation must fail before publish when a required Jenkins parameter is missing.
6. Secrets are referenced by Jenkins credential ids, never stored as raw secret values in DSL.
7. Backward compatibility keeps current generic node types working during migration, but new real nodes should be explicit.

## 3. Proposed Node Model

Extend `PipelineSpec.Node.params` with typed Jenkins fields and strengthen `PipelineNodeTypeRespVO.paramSchema`.

Common node fields, shared by all Jenkins nodes:

| Field | Meaning | Jenkins Mapping |
| --- | --- | --- |
| `agentLabel` | Optional Jenkins node label | stage `agent { label '...' }` |
| `toolJdk` | Optional Jenkins JDK tool name | stage `tools { jdk '...' }` |
| `toolMaven` | Optional Maven tool name | stage `tools { maven '...' }` |
| `toolNode` | Optional Node tool name if NodeJS plugin is used | stage environment/tool wrapper, implementation depends on plugin availability |
| `env` | Extra non-secret env map | stage `environment { KEY = 'value' }` |
| `timeoutSeconds` | Existing node timeout | stage `options { timeout(...) }` |
| `retryTimes` | Existing retry count | stage `options { retry(n) }` |
| `failStrategy` | Existing fail behavior | initially `FAIL_FAST`; future can map unstable/continue |

## 4. Node Types

### 4.1 `CHECKOUT`

Purpose: checkout platform-merged deploy branch.

Params:

| Field | Default | Required |
| --- | --- | --- |
| `cleanBeforeCheckout` | `true` | no |
| `checkoutSubdirectory` | empty | no |
| `shallowClone` | `false` | no |

Generated stage:

```groovy
stage('拉取代码') {
  steps {
    script {
      goneDevopsCallback(...)
      try {
        goneDevopsCheckout(repoUrl: params.REPO_URL, branchName: params.BRANCH_NAME, commitSha: params.COMMIT_SHA)
        goneDevopsCallback(...)
      } catch (err) {
        goneDevopsCallback(..., message: err.getMessage())
        throw err
      }
    }
  }
}
```

### 4.2 `MAVEN_BUILD_JAR`

Purpose: build backend jar package.

Params:

| Field | Default | Required | Jenkins Mapping |
| --- | --- | --- | --- |
| `workingDir` | `.` | yes | `dir(...)` |
| `goals` | `clean package` | yes | `sh` body |
| `profiles` | empty | no | `-P...` |
| `skipTests` | `true` | no | `-DskipTests` |
| `mavenOptions` | empty | no | `MAVEN_OPTS` / command suffix |
| `settingsConfigId` | empty | no | Config File Provider, if installed |
| `artifactPattern` | `**/target/*.jar` | yes | `archiveArtifacts` candidate |

Generated stage should call a shared-library wrapper such as:

```groovy
goneDevopsMavenBuildJar(
  workingDir: 'yudao-server',
  goals: 'clean package',
  profiles: 'prod',
  skipTests: true,
  artifactPattern: '**/target/*.jar'
)
```

The wrapper may internally run `sh 'mvn ...'` and can later support Jenkins config-file-provider without changing platform DSL.

### 4.3 `NPM_BUILD`

Purpose: build frontend assets.

Params:

| Field | Default | Required | Jenkins Mapping |
| --- | --- | --- | --- |
| `workingDir` | `.` | yes | `dir(...)` |
| `packageManager` | `npm` | yes | command selector |
| `installCommand` | `npm ci` | yes | `sh` |
| `buildCommand` | `npm run build` | yes | `sh` |
| `nodeVersionTool` | empty | no | Jenkins NodeJS tool name, if available |
| `distPattern` | `dist/**` | yes | archive/stash candidate |
| `cacheEnabled` | `false` | no | future npm cache support |

Generated wrapper call:

```groovy
goneDevopsNpmBuild(
  workingDir: 'frontend',
  installCommand: 'npm ci',
  buildCommand: 'npm run build',
  distPattern: 'dist/**'
)
```

### 4.4 `DOCKER_BUILD_PUSH`

Purpose: build Docker image, optionally push to registry.

Params:

| Field | Default | Required | Jenkins Mapping |
| --- | --- | --- | --- |
| `imageName` | `${APP_KEY}` | yes | `docker.build(name:tag, ...)` |
| `imageTagExpression` | `${COMMIT_SHA}` fallback build number | yes | tag expression |
| `dockerfile` | `Dockerfile` | yes | Docker build arg |
| `context` | `.` | yes | Docker build context |
| `buildArgs` | `{}` | no | `--build-arg` |
| `registryUrl` | empty | no | `docker.withRegistry(...)` |
| `registryCredentialsId` | empty | no | Jenkins credential id |
| `push` | `true` | no | image `push()` |
| `pushLatest` | `false` | no | image `push('latest')` |

Generated wrapper call:

```groovy
env.IMAGE_TAG = goneDevopsDockerBuildPush(
  imageName: params.APP_KEY,
  imageTag: params.COMMIT_SHA ?: env.BUILD_NUMBER,
  dockerfile: 'Dockerfile',
  context: '.',
  registryUrl: 'https://registry.example.com',
  credentialsId: 'docker-registry'
)
```

### 4.5 `ARTIFACT_UPLOAD`

Purpose: expose Jenkins-native artifact upload/archive as a platform node.

Confirmed MVP mode: Jenkins `archiveArtifacts`.

Params:

| Field | Default | Required | Jenkins Mapping |
| --- | --- | --- | --- |
| `artifactPattern` | `**/target/*.jar` or `dist/**` | yes | `archiveArtifacts artifacts` |
| `fingerprint` | `true` | no | `archiveArtifacts fingerprint` |
| `allowEmptyArchive` | `false` | no | `archiveArtifacts allowEmptyArchive` |
| `onlyIfSuccessful` | `true` | no | `archiveArtifacts onlyIfSuccessful` |
| `stashName` | empty | no | optional `stash` for same-run reuse |

Generated stage:

```groovy
archiveArtifacts artifacts: '**/target/*.jar',
  fingerprint: true,
  allowEmptyArchive: false,
  onlyIfSuccessful: true
```

External artifact repositories are out of scope for this phase. If product later requires Nexus/MinIO upload instead of Jenkins archive, add a second mode:

- `uploadMode`: `JENKINS_ARCHIVE` / `NEXUS` / `MINIO`
- `repositoryUrl`, `repositoryName`, `credentialsId`, `targetPath`

That requires a separate secret and repository configuration decision.

## 5. Compatibility Strategy

Keep existing node types:

- `CHECKOUT`: keep and strengthen params.
- `BUILD_ARTIFACT`: keep as deprecated compatibility alias. Existing `maven_package_skip_tests` maps to `MAVEN_BUILD_JAR`; existing `npm_build` maps to `NPM_BUILD`.
- `BUILD_IMAGE`: keep as deprecated compatibility alias for `DOCKER_BUILD_PUSH`.
- `REPORT_ARTIFACTS`: keep as deprecated compatibility alias for `ARTIFACT_UPLOAD`.
- `MOCK`: keep for smoke testing.

New drafts should use explicit node types. Published old versions must still generate the same Jenkinsfile behavior.

## 6. Backend Changes

### 6.1 Node Registry

Refactor `PipelineNodeRegistryServiceImpl` from ad hoc maps to typed node descriptors:

- `PipelineNodeDescriptor`
- `PipelineParamDescriptor`
- `PipelineNodeTemplate`

Expose `paramSchema` with enough metadata for frontend rendering:

```json
{
  "type": "object",
  "required": ["workingDir", "goals", "artifactPattern"],
  "properties": {
    "workingDir": {"type": "string", "title": "工作目录", "default": "."},
    "skipTests": {"type": "boolean", "title": "跳过测试", "default": true},
    "artifactPattern": {"type": "string", "title": "制品匹配", "default": "**/target/*.jar"}
  }
}
```

### 6.2 Validation

Enhance `PipelineSpecValidationServiceImpl`:

- Validate required params by node type.
- Validate boolean/string/map/list types.
- Validate image tag/name and Dockerfile/context presence.
- Validate `registryCredentialsId` is required when `registryUrl` plus `push=true` needs auth.
- Validate `artifactPattern` is non-blank for upload/archive.
- Validate old `commandTemplateKey` only for compatibility node types.
- Keep topology validation unchanged.

### 6.3 Jenkinsfile Generator

Refactor `JenkinsfileGeneratorServiceImpl` into handler-style generation:

- `JenkinsStageGenerator`
- one implementation per node type or node family.

Every stage should be generated as:

```groovy
stage('<node name>') {
  agent ...
  tools ...
  environment ...
  options ...
  steps {
    script {
      goneDevopsCallback(... STARTED ...)
      try {
        // node-specific Jenkins content
        goneDevopsCallback(... COMPLETED ...)
      } catch (err) {
        goneDevopsCallback(... FAILED ..., message: err.getMessage())
        throw err
      }
    }
  }
  post { ... }
}
```

Callback metadata must continue using stable `nodeId`, `nodeType`, and `nodeName`, independent of visible stage name.

### 6.4 Jenkins Shared Library

Current docs only define `goneDevopsCallback`. Add required shared library vars:

- `goneDevopsCheckout.groovy`
- `goneDevopsMavenBuildJar.groovy`
- `goneDevopsNpmBuild.groovy`
- `goneDevopsDockerBuildPush.groovy`
- optionally `goneDevopsArtifactUpload.groovy`

Reason: generated Jenkinsfile remains compact, Jenkins-specific implementation is testable in Jenkins, and platform DSL does not become raw shell.

### 6.5 Runtime Handler

Extend `JenkinsPipelineNodeRuntimeHandler.SUPPORTED_NODE_TYPES` to include:

- `MAVEN_BUILD_JAR`
- `NPM_BUILD`
- `DOCKER_BUILD_PUSH`
- `ARTIFACT_UPLOAD`

Existing generic aliases remain supported.

### 6.6 Runner Parameters

Keep existing runner parameters:

- `PIPELINE_RUN_ID`
- `PIPELINE_VERSION_ID`
- `REPO_URL`
- `BRANCH_NAME`
- `COMMIT_SHA`
- `APP_KEY`
- `CALLBACK_URL`
- `CALLBACK_TOKEN`
- `JENKINSFILE_TEXT`

Add only if needed:

- `IMAGE_REGISTRY`
- `DEFAULT_DOCKER_CREDENTIALS_ID`

Prefer node params for stage-specific values so published version is self-contained.

## 7. Frontend Contract

Frontend should:

- Fetch `/devops/pipeline/configurable-node-types`.
- Render node palette from backend `name/icon/category/enabled`.
- Render node config panel from backend `paramSchema`.
- Persist `specJson` with `params` exactly matching backend schema.
- Show generated Jenkinsfile preview from backend validation/publish response.
- Avoid hard-coded command templates and avoid raw shell fields.

Current repository does not contain usable frontend source; implementation may need a separate frontend checkout or later task.

## 8. Tests

Backend focused tests:

- `PipelineNodeRegistryServiceImplTest`
  - new node types exist;
  - param schemas include required Jenkins-equivalent fields;
  - deprecated aliases remain present.
- `PipelineSpecValidationServiceImplTest`
  - missing required Maven/NPM/Docker/artifact params fail validation;
  - valid nodes pass;
  - old `commandTemplateKey` compatibility still passes.
- `JenkinsfileGeneratorServiceImplTest`
  - each node generates exactly one `stage`;
  - stage name matches node name;
  - callbacks are present;
  - Maven jar stage includes Maven wrapper call and artifact pattern;
  - NPM stage includes install/build/dist pattern;
  - Docker stage includes build/push registry fields;
  - artifact stage includes `archiveArtifacts`.
- `PipelineJenkinsCallbackServiceImplTest`
  - callbacks for new node types are accepted.

Verification commands:

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile
mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='*Pipeline*Test,JenkinsfileGeneratorServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

## 9. Rollout Plan

Phase A: Backend DSL and generator

- Add explicit node descriptors and schemas.
- Add validation rules.
- Add Jenkins stage generators.
- Update tests.

Phase B: Jenkins shared library docs/runtime contract

- Update `JENKINS_RUNNER_CONFIGURATION.md`.
- Add examples for Maven, NPM, Docker, and artifact archive wrappers.

Phase C: Frontend designer

- Add schema-driven node parameter form when frontend code is available.
- Keep API contracts backend-first.

Phase D: Jenkins integration smoke test

- Publish pipeline: checkout -> maven jar -> docker build push -> artifact archive.
- Publish pipeline: checkout -> npm build -> artifact archive.
- Trigger through existing release-submit flow and verify platform logs.

## 10. Confirmed Decision

`ARTIFACT_UPLOAD` means Jenkins-native `archiveArtifacts` for this phase.

Rationale: it is Jenkins-native, requires no new secret store, and matches the current Runner architecture. External repository upload can be added later as a node mode once Nexus/MinIO target details are known.
