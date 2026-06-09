# Fix Jenkins Tool Descriptor Fallback

## Goal

Fix Jenkins tool list lookup when `/descriptorByName/hudson.model.JDK/api/json` returns 404 on Jenkins instances that expose the descriptor by implementation id instead.

## What I Already Know

* The reported failing URL is `https://test-jenkins.gigimed.cn/descriptorByName/hudson.model.JDK/api/json?tree=installations%5Bname,home%5D`.
* Current implementation returns an empty list immediately on descriptor 404.
* Some Jenkins descriptors are reachable through `$DescriptorImpl` ids, for example `hudson.model.JDK$DescriptorImpl`.
* We should keep using Jenkins JSON APIs and not require Script Console permissions for a read-only dropdown.

## Requirements

* Try descriptor id candidates for JDK and Maven.
* On 404 for one candidate, continue to the next candidate.
* Return empty only when all candidates 404.
* Keep non-404 Jenkins request failures mapped to `PIPELINE_JENKINS_TOOL_FETCH_FAIL`.
* Add focused tests for fallback success and all-candidates-404 behavior.

## Acceptance Criteria

* [ ] `type=JDK` tries `hudson.model.JDK` and then `hudson.model.JDK$DescriptorImpl` when the first returns 404.
* [ ] `type=MAVEN` has the same fallback behavior with Maven descriptor ids.
* [ ] Existing tool-list tests continue passing.

## Out of Scope

* Switching to Jenkins `/scriptText`.
* Creating or mutating Jenkins tool configuration.
