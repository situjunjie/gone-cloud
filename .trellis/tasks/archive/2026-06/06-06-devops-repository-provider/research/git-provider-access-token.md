# GitLab Access Token Research

## GitLab4J

* GitLab4J API is a Java client library for the GitLab REST API.
* Version `6.3.0` is documented in the project README and requires Java 11+.
* Maven coordinate: `org.gitlab4j:gitlab4j-api:6.3.0`.
* Personal access token usage: `new GitLabApi("http://your.gitlab.server.com", "YOUR_PERSONAL_ACCESS_TOKEN")`.
* Project listing usage: `gitLabApi.getProjectApi().getProjects()`.

## GitLab

* Official REST API docs support personal access token authentication using the `PRIVATE-TOKEN` header.
* For an access-token based MVP, call `GET /user` to validate the token and identify the account.
* Project listing can start with `GET /projects?membership=true`.
* GitLab API base URL is commonly `<serverUrl>/api/v4`, but private deployments should allow an explicit API URL.

References:

* https://docs.gitlab.com/api/rest/authentication/
* https://docs.gitlab.com/api/users/#for-normal-users
* https://docs.gitlab.com/api/projects/
* https://github.com/gitlab4j/gitlab4j-api

## Local Design Notes

* Keep provider type and auth type as numeric enums to match existing Yudao-style dictionaries.
* Store the access token on the provider DO but omit it from normal response VOs.
* First integration surface should be a small service abstraction with `testConnection` and `listRepositories`.
