package cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * GitLab Push Hook 请求载荷。
 */
@Data
public class RepositoryProviderGitLabPushHookReqVO {

    @JsonProperty("object_kind")
    private String objectKind;

    @JsonProperty("event_name")
    private String eventName;

    private String ref;

    private String after;

    @JsonProperty("checkout_sha")
    private String checkoutSha;

    private Project project;

    private List<Commit> commits;

    @Data
    public static class Project {

        private Long id;

        @JsonProperty("path_with_namespace")
        private String pathWithNamespace;

    }

    @Data
    public static class Commit {

        private String id;

        private String message;

        private String timestamp;

    }

}
