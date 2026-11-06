# Directory Structure

This repository is a multi-module Maven backend built around Spring Boot 3, Spring Cloud Gateway, and Yudao framework starters. New code should extend the existing module split instead of creating ad hoc folders under `yudao-server`.

## Top-Level Layout

```text
.
├── pom.xml
├── yudao-dependencies/          # dependency BOM
├── yudao-framework/             # shared framework starters and common infra
├── yudao-gateway/               # Spring Cloud Gateway application
├── yudao-server/                # main admin/server boot application
├── yudao-module-*/              # business domains, usually split into api/server
├── sql/                         # database bootstrap scripts for multiple engines
└── yudao-ui/                    # frontend apps, outside this backend spec
```

Examples:

- Root module aggregation lives in [pom.xml](/Users/situjunjie/projects/gone-cloud/pom.xml:1).
- The main server app entrypoint lives in [YudaoServerApplication.java](/Users/situjunjie/projects/gone-cloud/yudao-server/src/main/java/cn/iocoder/yudao/server/YudaoServerApplication.java:1).
- The gateway app entrypoint lives in [GatewayServerApplication.java](/Users/situjunjie/projects/gone-cloud/yudao-gateway/src/main/java/cn/iocoder/yudao/gateway/GatewayServerApplication.java:1).

## Module Split

Use the existing responsibilities:

- `yudao-framework/`: reusable starters, base abstractions, cross-cutting infra.
- `yudao-gateway/`: edge concerns only, such as auth forwarding, CORS, access logging, route handling, and reactive exception handling.
- `yudao-server/`: boot app wiring and top-level controllers that belong to the composed application.
- `yudao-module-<domain>/yudao-module-<domain>-api/`: public DTOs, enums, events, and RPC-facing contracts shared across modules.
- `yudao-module-<domain>/yudao-module-<domain>-server/`: the domain's controllers, services, persistence, and local framework glue.
- `yudao-module-iot/yudao-module-iot-gateway/`: protocol-side runtime that is separate from admin/server concerns.

Do not put new business code straight into `yudao-server/` if it belongs to a domain module. Follow the existing `api` plus `server` split when the capability is reusable outside one boot app.

## In-Module Package Layout

Inside a `*-server` module, the dominant structure is:

```text
src/main/java/cn/iocoder/yudao/module/<domain>/
├── controller/
│   ├── admin/
│   └── app/
├── service/
├── dal/
│   ├── dataobject/
│   └── mysql/
├── convert/
├── enums/
├── framework/                  # only when the domain owns custom integration code
└── job/ / mq/ / api/ / other specialized packages as needed
```

Real examples:

- Persistence split in [ImRtcCallDO.java](/Users/situjunjie/projects/gone-cloud/yudao-module-im/yudao-module-im-server/src/main/java/cn/iocoder/yudao/module/im/dal/dataobject/rtc/ImRtcCallDO.java:1) and [ImRtcCallMapper.java](/Users/situjunjie/projects/gone-cloud/yudao-module-im/yudao-module-im-server/src/main/java/cn/iocoder/yudao/module/im/dal/mysql/rtc/ImRtcCallMapper.java:1).
- Admin controller packages under `controller/admin/...` appear throughout `yudao-module-system`, `yudao-module-im`, and `yudao-module-mes`.
- Domain-specific framework code exists under modules such as `yudao-module-iot/.../framework/`.

## Naming Conventions

- Maven modules use `yudao-module-<domain>[-subdomain]`.
- Java base package stays under `cn.iocoder.yudao`.
- Data objects end with `DO`.
- Mapper interfaces end with `Mapper`.
- Request/response payloads commonly end with `ReqVO`, `RespVO`, `PageReqVO`, or `DTO`.
- Boot applications end with `Application`.
- Error-code catalogs live in `enums/ErrorCodeConstants.java`.

## Placement Rules

- New HTTP endpoints go in `controller/admin` or `controller/app` based on audience.
- Business logic belongs in `service`, not controllers or mappers.
- SQL composition belongs in mapper default methods, usually with `LambdaQueryWrapperX`.
- Shared enums, public DTOs, and inter-module events belong in `*-api`.
- Cross-cutting infra should prefer `yudao-framework` starters over duplicating utility code in one domain.

## Anti-Patterns

- Do not add new domain folders directly under `yudao-server/src/main/java/.../server` when a `yudao-module-*` module is the natural home.
- Do not bypass the `api/server` split by importing `*-server` classes across modules for public contracts.
- Do not mix persistence classes, controllers, and services into one package.
