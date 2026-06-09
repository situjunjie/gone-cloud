# Fix Jenkins Tool Lookup 404

## Goal

Make Jenkins JDK/Maven tool lookup work on Jenkins instances where `/descriptorByName/...` returns 404 by trying management-route descriptor URLs, the correct Maven installation descriptor id, and a Script Console fallback.

## What I Already Know

* The reported URL still returns 404: `/descriptorByName/hudson.model.JDK/api/json?tree=installations[name,home]`.
* Jenkins tool lookup should remain read-only and preserve selected params as tool `name` strings.
* Descriptor routes vary by Jenkins version and tool descriptor class.
* `/scriptText` can read tool installations but requires a highly privileged Jenkins user.

## Requirements

* Try descriptor candidates across both root and `/manage` descriptor routes.
* Include Maven installation descriptor ids, not only the Maven builder descriptor.
* If descriptor JSON APIs all return 404, fallback to `/scriptText` Groovy read.
* Preserve current non-404 failure handling.
* Add focused unit tests for manage-route fallback and script fallback.

## Acceptance Criteria

* [ ] Tool lookup tries `/manage/descriptorByName/...` after root descriptor candidates 404.
* [ ] Maven lookup includes `hudson.tasks.Maven$MavenInstallation`.
* [ ] Script fallback parses JSON output into tool options.
* [ ] Focused Jenkins client tests pass.

## Out of Scope

* Creating or changing Jenkins global tools.
* Persisting Jenkins tool list in the database.
