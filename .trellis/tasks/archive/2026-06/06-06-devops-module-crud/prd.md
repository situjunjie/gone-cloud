# DevOps Module CRUD

## Goal

Create a new `yudao-module-devops` module following the existing Yudao module structure, and implement admin CRUD APIs for the DevOps tables from `sql/mysql/devops.sql`.

## Confirmed Package Structure

```text
yudao-module-devops/
├── yudao-module-devops-api/
│   └── src/main/java/cn/iocoder/yudao/module/devops/
│       └── enums/
│           ├── DictTypeConstants.java
│           └── ErrorCodeConstants.java
└── yudao-module-devops-server/
    └── src/main/java/cn/iocoder/yudao/module/devops/
        ├── controller/admin/
        ├── service/
        ├── dal/
        │   ├── dataobject/
        │   └── mysql/
        └── convert/
```

Business packages:

```text
application/
change/
environment/
```

## Requirements

- Add `yudao-module-devops` to the Maven reactor.
- Create `api` and `server` submodules.
- Implement CRUD for main business tables:
  - `dev_application`
  - `dev_environment`
  - `dev_change`
- Do not expose standalone CRUD controllers for relationship tables:
  - `dev_application_env`
  - `dev_change_env`
- Maintain `dev_application_env` through application business APIs.
- Maintain `dev_change_env` through change business APIs.
- Use existing project patterns:
  - `TenantBaseDO`
  - `BaseMapperX`
  - `LambdaQueryWrapperX`
  - `SaveReqVO`, `PageReqVO`, `RespVO`
  - business class names without module-name prefix, such as `ApplicationDO` instead of `DevopsApplicationDO`
  - MapStruct converter interfaces
  - `CommonResult`
  - `ServiceExceptionUtil.exception`
  - `@PreAuthorize`

## Proposed Admin APIs

Application:

- `POST /devops/application/create`
- `PUT /devops/application/update`
- `DELETE /devops/application/delete`
- `GET /devops/application/get`
- `GET /devops/application/page`
- `PUT /devops/application/update-envs`

Environment:

- `POST /devops/environment/create`
- `PUT /devops/environment/update`
- `DELETE /devops/environment/delete`
- `GET /devops/environment/get`
- `GET /devops/environment/page`

Change:

- `POST /devops/change/create`
- `PUT /devops/change/update`
- `DELETE /devops/change/delete`
- `PUT /devops/change/release`
- `PUT /devops/change/discard`
- `GET /devops/change/get`
- `GET /devops/change/page`
- `POST /devops/change/mount-env`
- `PUT /devops/change/unmount-env`

## Acceptance Criteria

- [x] Maven can compile the new DevOps module server.
- [x] Main table CRUD endpoints exist for application, environment, and change.
- [x] Relationship tables have DO/Mapper support but no standalone CRUD controllers.
- [x] Application environment relations can be replaced from application API.
- [x] Change environment relation can be mounted and unmounted from change API.
- [x] Errors use DevOps error-code constants.
- [x] Dict type constants match `sql/mysql/devops-dict.sql`.

## Out of Scope

- Frontend pages and menus.
- Pipeline execution engine.
- Git provider integration.
- Approval workflow integration.
- Docker/Jenkins deployment changes for the new module.
