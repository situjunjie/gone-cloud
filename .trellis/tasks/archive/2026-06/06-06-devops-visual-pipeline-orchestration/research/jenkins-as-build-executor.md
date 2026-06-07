# Jenkins as build/test artifact executor

## Positioning

Jenkins should be positioned as the executor between code merge and deployment, not as the owner of the whole DevOps workflow.

Platform owns:

* application, environment, repository, change, and deployment domain state
* visual orchestration DSL
* Jenkinsfile generation and version audit
* triggering Jenkins
* observing build/test stages
* collecting artifacts
* approval and deployment decision
* deployment execution and run history

Jenkins owns:

* checkout repository and branch
* run unit tests
* build package/image/chart
* publish test report/artifacts
* expose logs and stage states

## Input/output contract

Input to Jenkins:

* Jenkinsfile or stable bootstrap Jenkinsfile
* repository URL or repository identifier
* branch name or commit SHA
* pipeline run id
* pipeline version id
* application id
* application environment id
* change environment id
* optional build parameters

Output from Jenkins:

* Jenkins queue id
* job path
* build number
* build URL
* stage status
* console/stage logs
* test report URL or summary
* archived artifacts
* image tag and digest if building container image
* final result

## Recommended flow

1. Platform merges or prepares the branch according to DevOps change rules.
2. Platform creates `PipelineRun`.
3. Platform triggers Jenkins with repository and branch/commit parameters.
4. Jenkins checks out source code.
5. Jenkins executes unit test and build stages.
6. Jenkins archives artifacts and/or pushes image to registry.
7. Platform polls Jenkins APIs or receives callback.
8. Platform records artifact metadata in `PipelineArtifact`.
9. Platform enters approval if the target environment requires it.
10. Platform deploys the approved artifact to Kubernetes/host environment.

## Jenkinsfile shape

Generated Jenkinsfile should focus on test/build/artifact stages:

```groovy
@Library('gone-devops-shared') _

pipeline {
  agent any
  options {
    timestamps()
    disableConcurrentBuilds()
  }
  parameters {
    string(name: 'PIPELINE_RUN_ID')
    string(name: 'REPO_URL')
    string(name: 'BRANCH_NAME')
    string(name: 'COMMIT_SHA', defaultValue: '')
    string(name: 'APP_KEY')
  }
  stages {
    stage('checkout__CHECKOUT') {
      steps {
        goneDevopsCheckout(
          repoUrl: params.REPO_URL,
          branchName: params.BRANCH_NAME,
          commitSha: params.COMMIT_SHA
        )
      }
    }
    stage('unit_test__UNIT_TEST') {
      steps {
        goneDevopsUnitTest(command: 'mvn test')
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: '**/surefire-reports/*.xml'
        }
      }
    }
    stage('build_artifact__BUILD_ARTIFACT') {
      steps {
        goneDevopsBuildArtifact(command: 'mvn -DskipTests package')
        archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
      }
    }
    stage('build_image__BUILD_IMAGE') {
      steps {
        script {
          env.IMAGE_TAG = goneDevopsBuildImage(appKey: params.APP_KEY)
        }
      }
    }
    stage('report_artifacts__REPORT_ARTIFACTS') {
      steps {
        goneDevopsReportArtifacts(
          pipelineRunId: params.PIPELINE_RUN_ID,
          imageTag: env.IMAGE_TAG
        )
      }
    }
  }
}
```

Deployment is intentionally not inside Jenkins in this model. Deployment happens in the platform after artifact collection and approval.

## Why this boundary is better

* Platform keeps control of environment permissions and approval rules.
* Jenkins remains replaceable as a build provider.
* Deployment state stays consistent with platform environment and change models.
* Build artifacts become explicit platform records instead of implicit Jenkins side effects.
* Approval does not need to pause Jenkins unless the product explicitly wants that behavior.

## Open implementation choices

### Jenkinsfile handoff

Recommended production choices:

* Store Jenkinsfile in platform DB for audit, then write to pipeline Git repo for Jenkins SCM execution.
* Or use a fixed Jenkins bootstrap job and let Jenkins fetch build plan by `PIPELINE_RUN_ID` from platform.

Avoid relying on `buildWithParameters` to pass raw Jenkinsfile text as the Pipeline definition. Jenkins build parameters do not normally replace a Pipeline job's script definition.

### Artifact reporting

Options:

* Platform polls Jenkins artifact APIs and test report APIs.
* Jenkins Shared Library calls platform API at the end of each stage.
* Hybrid: polling for baseline, callback for faster UI updates.

Recommended MVP:

* Poll Jenkins stage/build status.
* Use a final `report_artifacts` stage to call platform API with image tag, digest, artifact URLs, and report URLs.

### Branch input

Prefer commit SHA over branch name once the run starts. Branches move; commits do not.

Trigger parameters can include both:

* `BRANCH_NAME` for checkout intent and display
* `COMMIT_SHA` for immutable build input

## Data model impact

The platform still needs:

* `PipelineRun`
* `PipelineStageRun`
* `PipelineArtifact`
* `PipelineJenkinsBuild`
* `PipelineLogCursor`

`PipelineArtifact` should include:

* `run_id`
* `stage_run_id`
* `artifact_type`: JAR, IMAGE, CHART, TEST_REPORT, OTHER
* `name`
* `version`
* `url`
* `image_tag`
* `image_digest`
* `checksum`
* `metadata_json`

Deployment should reference `PipelineArtifact`, not just a Jenkins build number.
