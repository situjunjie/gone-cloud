# Controller LocalDate timestamp serialization

## Goal

Return `LocalDate` fields in controller JSON responses as epoch milliseconds so API consumers receive date values in the same numeric timestamp form as existing `LocalDateTime` fields.

## Confirmed Facts

- Servlet controllers receive Jackson configuration from `yudao-framework/yudao-spring-boot-starter-web` via `YudaoJacksonAutoConfiguration`.
- Gateway JSON uses a matching, duplicated configuration in `yudao-gateway`.
- Both configurations currently serialize and deserialize `LocalDate` with Jackson's ISO-date handlers, while `LocalDateTime` uses custom epoch-millisecond handlers.
- The current `LocalDateTime` epoch-millisecond handlers use `ZoneId.systemDefault()`.
- `LocalDate` occurs in both request and response VOs, including project sprint/worklog APIs and statistics APIs.

## Requirements


- R1: Controller JSON responses containing `LocalDate` must emit a numeric epoch-millisecond value representing the start of that calendar day in the agreed timezone.
- R2: The start-of-day conversion uses `ZoneId.systemDefault()`, matching the existing `LocalDateTime` timestamp serializer.
- R3: Request deserialization behavior remains unchanged: `LocalDate` request values continue to use the existing ISO-date handler.
- R4: Register the behavior in the shared servlet Jackson configuration, so all Spring MVC controller responses use it without per-VO annotations.
- R5: Add focused serialization coverage for the selected conversion semantics.

## Acceptance Criteria

- [ ] A `LocalDate` in a servlet-controller JSON response serializes as a JSON number with the expected epoch-millisecond value.
- [ ] Existing `LocalDateTime` timestamp behavior remains unchanged.
- [ ] ISO-date `LocalDate` request payloads still deserialize successfully.
- [ ] Focused tests pass for the touched framework/gateway configuration.

## Out of Scope

- Changing the Java type of API fields or database columns.
- Bulk changes to individual controller VO annotations.
- Changing response behavior for `LocalTime`, `Date`, `Instant`, or `LocalDateTime`.
- Changing `LocalDate` request payload formats.
- Changing the gateway's independent WebFlux Jackson configuration; the requested controller API behavior is owned by the servlet web starter.

## Technical Notes

- A dedicated `LocalDate` serializer will use `LocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()`.
- The shared servlet auto-configuration will replace only the `LocalDate` serializer registration; its existing `LocalDateDeserializer` registration remains in place.
