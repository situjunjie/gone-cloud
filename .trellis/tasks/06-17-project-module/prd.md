# Project Module

## Goal

Create a new `project` business module for future project-management capabilities. The module should follow the existing `devops` module structure, be usable inside the `yudao-server` monolith now, and remain deployable as an independent service later.

## What I Already Know

* User wants a new Maven module named `project` for project-management functionality.
* The module should reference the current `devops` module structure.
* The module must be integrated into the `yudao-server` monolith.
* The module should also be prepared for distributed deployment later.
* Root `pom.xml` currently enables `yudao-module-system`, `yudao-module-infra`, `yudao-module-bpm`, and `yudao-module-devops` in the Maven reactor.
* `yudao-server/pom.xml` integrates modules by depending on each `*-server` artifact.
* `yudao-module-devops` uses a parent module with `yudao-module-devops-api` and `yudao-module-devops-server`.
* `yudao-module-devops-server` has its own `SpringBootApplication`, `Dockerfile`, `application*.yaml`, and is listed in Jenkins/Compose for distributed deployment.

## Requirements

* Add a top-level Maven module `yudao-module-project`.
* Add submodules:
  * `yudao-module-project-api`
  * `yudao-module-project-server`
* Follow package base `cn.iocoder.yudao.module.project`.
* Add an independent `ProjectServerApplication` entrypoint for distributed deployment.
* Add `project-server` Spring Boot configuration files analogous to `devops-server`.
* Add a Dockerfile for `yudao-module-project-server`.
* Add `yudao-module-project` to the root Maven reactor.
* Add `yudao-module-project-server` dependency to `yudao-server` for monolith startup.
* Add distributed deployment wiring analogous to `devops-server`:
  * Jenkins service map and checkbox.
  * Docker Compose service entry.
  * Standalone deployment README references/examples.
  * `.env.example` port/JAVA_OPTS entries if required by existing template style.
* Keep business implementation minimal unless explicitly included in MVP.
* MVP scope is scaffolding only: no project-management CRUD, tables, menu permissions, or business endpoints in this task.

## Acceptance Criteria

* [ ] `mvn -pl yudao-module-project/yudao-module-project-server -am test` can resolve and compile the new module.
* [ ] `mvn -pl yudao-server -am test` can resolve the monolith with the project module dependency.
* [ ] `project-server` can be packaged as an independent Spring Boot jar.
* [ ] Jenkins and Compose service names stay consistent.
* [ ] No project-management business API is exposed unless included in scope.

## Definition of Done

* Tests or compile verification run for affected Maven modules.
* Deployment files validate if Compose changes are made.
* Docs/notes updated where deployment behavior changes.
* Rollout/rollback considered for module registration and deployment selection.

## Out of Scope

* Full project-management domain model, tables, menus, permissions, and CRUD APIs, unless user confirms those should be included in this task.
* Frontend implementation.
* Data migration for future project-management tables.
* Cross-module RPC contracts beyond empty module/API scaffolding.

## Technical Approach

Mirror `yudao-module-devops`:

* Parent POM under `yudao-module-project`.
* API submodule depending on `yudao-common`.
* Server submodule depending on:
  * `yudao-module-project-api`
  * platform APIs it needs at bootstrap, likely `yudao-module-system-api`
  * common Yudao starters for env, tenant, security, mybatis, redis, rpc, monitor, test
  * Nacos discovery/config for independent deployment
* Keep Java packages empty/minimal except for the boot application and a module error-code catalog if needed by compilation or future conventions.
* Use a distinct service name and port for independent deployment. Suggested:
  * service name: `project-server`
  * artifact/image: `yudao-module-project-server`
  * default port: `48095` because `devops-server` uses `48094`.

## Decision (ADR-lite)

**Context**: The repository supports both monolith startup through `yudao-server` and independent microservice startup through each module's `*-server` artifact.

**Decision**: Create the project module with the same `api/server` split and deployment shape as `devops`, then add it to both monolith and distributed deployment wiring.

**Consequences**: This adds a little scaffold now, but avoids later reshaping when project management becomes a standalone service. The main risk is over-scoping into business CRUD before the aggregate boundaries are clear.

## Open Questions

* None.

## Technical Notes

* Relevant specs read:
  * `.trellis/spec/backend/index.md`
  * `.trellis/spec/backend/directory-structure.md`
  * `.trellis/spec/backend/database-guidelines.md`
  * `.trellis/spec/backend/deployment-guidelines.md`
  * `.trellis/spec/backend/quality-guidelines.md`
* Relevant code inspected:
  * `pom.xml`
  * `yudao-server/pom.xml`
  * `yudao-module-devops/pom.xml`
  * `yudao-module-devops/yudao-module-devops-api/pom.xml`
  * `yudao-module-devops/yudao-module-devops-server/pom.xml`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/DevopsServerApplication.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/resources/application.yaml`
  * `yudao-module-devops/yudao-module-devops-server/Dockerfile`
  * `Jenkinsfile`
  * `script/docker/standalone/docker-compose.yml`
  * `script/docker/standalone/README.md`
