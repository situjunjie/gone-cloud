# add Kubernetes pod terminal

## Goal

Add a reusable DevOps Kubernetes Pod terminal capability. The terminal must not depend on deployment orders; deployment order detail is only one possible frontend entry point.

## Requirements

- Open a terminal by `environmentId`, `namespace`, `podName`, and optional `containerName`.
- Resolve Kubernetes connection material from the DevOps environment.
- Reject non-Kubernetes environments, missing Pods, non-running Pods, and ambiguous multi-container Pods.
- Use a WebSocket stream between frontend and backend.
- Use the existing token-based WebSocket login flow; do not introduce a new fine-grained permission point in this iteration.
- Keep the API generic so future Kubernetes Pod pages can reuse the same terminal endpoint.

## API Contract

Frontend connects to:

```text
WS /devops/kubernetes/pods/terminal?environmentId={id}&namespace={namespace}&podName={podName}&containerName={containerName}&token={token}
```

Client messages:

```json
{"type":"input","data":"ls -al\r"}
{"type":"resize","cols":120,"rows":32}
{"type":"close"}
```

Server messages:

```json
{"type":"output","data":"..."}
{"type":"error","message":"Pod is not running"}
{"type":"closed","reason":"session closed"}
```

## Frontend Integration Notes

The management frontend source is not present in this repository. The intended UI is:

- Add a terminal action on each Pod row in the Kubernetes current-status table.
- Pass `environmentId`, `namespace`, `podName`, and `containerName` from the selected row/detail context.
- Render the session with `xterm.js` and send resize events through the protocol above.

## Acceptance Criteria

- [ ] DevOps server registers a dedicated terminal WebSocket endpoint.
- [ ] WebSocket handshake requires an authenticated user through the existing token flow.
- [ ] Backend validates environment, namespace, Pod, and container before exec.
- [ ] Backend streams stdin/stdout between WebSocket and Kubernetes exec.
- [ ] Focused unit tests cover Pod/container validation behavior.
- [ ] DevOps server module compiles.

## Out of Scope

- New frontend files in this repository, because the admin frontend source is not present.
- New menu permission or SQL menu seed changes.
- Session recording, command audit transcript, file upload/download, and shared terminals.
