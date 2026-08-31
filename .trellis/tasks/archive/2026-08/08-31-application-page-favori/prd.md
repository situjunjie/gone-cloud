# Goal

Add a `favori` Boolean query parameter to `GET /devops/application/page` so callers can filter applications by whether the current user has favorited them.

# Confirmed Facts

- The endpoint accepts `ApplicationPageReqVO` and delegates pagination to `ApplicationMapper.selectPage`.
- User favorites are stored in `system_user_favorite` with business type `DEVOPS_APPLICATION`, tenant ID, user ID, and application ID (`biz_id`).
- Existing favorite APIs expose the business type constant as the string `DEVOPS_APPLICATION`.

# Requirements

- Add the requested `favori` Boolean field to the page request contract.
- Apply the favorite-state filter using the current authenticated user and tenant context when the parameter is provided.
- Preserve existing behavior and pagination when the parameter is omitted.

# Acceptance Criteria

- API documentation/request binding exposes an optional Boolean `favori` query parameter.
- `favori=true` and `favori=false` each produce the corresponding favorite-state result set according to the final agreed semantics.
- Omitting `favori` leaves the current application page results unchanged.
- Focused automated tests cover true, false, and omitted behavior where feasible.

# Out Of Scope

- Changes to favorite create/cancel endpoints or favorite storage schema.
- Changes to application response fields or sorting.

# Key Decision

- `favori=true` returns only applications favorited by the current user.
- `favori=false` returns only applications not favorited by the current user.
- An omitted `favori` value leaves the existing unfiltered behavior unchanged.
