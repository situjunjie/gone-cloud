# JSON Serialization Guidelines

## Scenario: LocalDate Controller Response Timestamps

### 1. Scope / Trigger

- Trigger: a Spring MVC controller response contains a `java.time.LocalDate` field.
- Scope: `yudao-framework/yudao-spring-boot-starter-web` Jackson configuration and its shared serializer; do not add per-VO annotations for the default contract.

### 2. Signatures

- `TimestampLocalDateSerializer extends JsonSerializer<LocalDate>` serializes `LocalDate` values.
- `YudaoJacksonAutoConfiguration` registers it with both `serializerByType(LocalDate.class, ...)` and the shared `Module` bean.

### 3. Contracts

- A non-null `LocalDate` response field is a JSON number: `date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()`.
- `LocalDate` request fields retain `LocalDateDeserializer.INSTANCE`; ISO `yyyy-MM-dd` request JSON stays supported.
- `LocalDateTime` continues to use its existing epoch-millisecond serializer and deserializer.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Controller response has a non-null `LocalDate` | Emit the server-default-timezone start-of-day epoch milliseconds |
| Controller response has a null `LocalDate` | Jackson's normal null handling applies |
| Request body has ISO `yyyy-MM-dd` | Deserialize through `LocalDateDeserializer.INSTANCE` |
| Request body has an invalid date string | Jackson reports the normal request-conversion error |

### 5. Good / Base / Bad Cases

- Good: register one shared serializer in the servlet starter so every Spring MVC Controller response follows the contract.
- Base: preserve the existing ISO-date deserializer while only changing response serialization.
- Bad: add `@JsonFormat` or manual epoch conversion to individual response VOs, which makes the API contract inconsistent.

### 6. Tests Required

- Test `YudaoJacksonAutoConfiguration` with its `Jackson2ObjectMapperBuilderCustomizer`.
- Assert `LocalDate` JSON is the server-default-timezone start-of-day epoch milliseconds.
- Assert an ISO `LocalDate` JSON value still deserializes.
- Assert `LocalDateTime` remains an epoch-millisecond JSON value.

### 7. Wrong vs Correct

#### Wrong

```java
.serializerByType(LocalDate.class, LocalDateSerializer.INSTANCE)
```

This returns a date string while `LocalDateTime` returns an epoch-millisecond number.

#### Correct

```java
.serializerByType(LocalDate.class, TimestampLocalDateSerializer.INSTANCE)
.deserializerByType(LocalDate.class, LocalDateDeserializer.INSTANCE)
```

This keeps the response timestamp contract and existing request compatibility separate.
