package cn.iocoder.yudao.module.devops.framework.jenkins;

import com.fasterxml.jackson.databind.JsonNode;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import jakarta.annotation.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_JENKINS_CONFIG_INVALID;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_JENKINS_CONSOLE_FETCH_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_JENKINS_TOOL_FETCH_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_JENKINS_TRIGGER_FAIL;

/**
 * Jenkins Pipeline HTTP client.
 */
@Component
public class JenkinsPipelineClientImpl implements JenkinsPipelineClient {

    public static final String TOOL_TYPE_JDK = "JDK";
    public static final String TOOL_TYPE_MAVEN = "MAVEN";

    private static final String[] DESCRIPTOR_ROUTES = {"descriptorByName", "manage/descriptorByName"};
    private static final String[] DESCRIPTORS_JDK = {"hudson.model.JDK", "hudson.model.JDK$DescriptorImpl"};
    private static final String[] DESCRIPTORS_MAVEN = {"hudson.tasks.Maven$MavenInstallation",
            "hudson.tasks.Maven$MavenInstallation$DescriptorImpl", "hudson.tasks.Maven",
            "hudson.tasks.Maven$DescriptorImpl"};
    private static final String SCRIPT_DESCRIPTOR_JDK = "hudson.model.JDK$DescriptorImpl";
    private static final String SCRIPT_DESCRIPTOR_MAVEN = "hudson.tasks.Maven$MavenInstallation$DescriptorImpl";

    @Resource
    private JenkinsProperties properties;
    @Resource
    private RestTemplate restTemplate;

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(properties.getEnabled());
    }

    @Override
    public List<JenkinsToolInstallation> getToolInstallations(String type) {
        if (!isEnabled()) {
            return List.of();
        }
        validateBaseConfig();
        String normalizedType = StrUtil.blankToDefault(type, "").trim().toUpperCase(Locale.ROOT);
        if (StrUtil.isBlank(normalizedType)) {
            List<JenkinsToolInstallation> tools = new ArrayList<>();
            tools.addAll(fetchToolInstallations(TOOL_TYPE_JDK, DESCRIPTORS_JDK));
            tools.addAll(fetchToolInstallations(TOOL_TYPE_MAVEN, DESCRIPTORS_MAVEN));
            return tools;
        }
        return switch (normalizedType) {
            case TOOL_TYPE_JDK -> fetchToolInstallations(TOOL_TYPE_JDK, DESCRIPTORS_JDK);
            case TOOL_TYPE_MAVEN -> fetchToolInstallations(TOOL_TYPE_MAVEN, DESCRIPTORS_MAVEN);
            default -> throw exception(PIPELINE_JENKINS_CONFIG_INVALID, "不支持的工具类型：" + type);
        };
    }

    @Override
    public JenkinsPipelineStartResult startPipeline(JenkinsPipelineStartRequest request) {
        if (!isEnabled()) {
            return new JenkinsPipelineStartResult(true, null);
        }
        validateTriggerConfig();
        String url = buildJobUrl("buildWithParameters");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("PIPELINE_RUN_ID", String.valueOf(request.getPipelineRunId()));
        form.add("PIPELINE_VERSION_ID", String.valueOf(request.getPipelineVersionId()));
        form.add("REPO_URL", request.getRepoUrl());
        form.add("BRANCH_NAME", request.getBranchName());
        form.add("COMMIT_SHA", StrUtil.blankToDefault(request.getCommitSha(), ""));
        form.add("APP_KEY", request.getAppKey());
        form.add("CALLBACK_URL", properties.getCallbackUrl());
        form.add("CALLBACK_TOKEN", properties.getCallbackToken());
        form.add("JENKINSFILE_TEXT", request.getJenkinsfileText());
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(form, buildHeaders()),
                    String.class);
            String queueId = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            return new JenkinsPipelineStartResult(false, queueId);
        } catch (RestClientException ex) {
            throw exception(PIPELINE_JENKINS_TRIGGER_FAIL, StrUtil.subPre(ex.getMessage(), 500));
        }
    }

    @Override
    public void stopPipeline(String jobName, String buildNumber) {
        if (!isEnabled() || StrUtil.isBlank(buildNumber)) {
            return;
        }
        validateJobConfig();
        String url = buildJobUrl(buildNumber + "/stop");
        try {
            restTemplate.postForEntity(url, new HttpEntity<>(buildHeaders()), String.class);
        } catch (RestClientException ex) {
            throw exception(PIPELINE_JENKINS_TRIGGER_FAIL, StrUtil.subPre(ex.getMessage(), 500));
        }
    }

    @Override
    public JenkinsConsoleChunk getConsoleText(String buildNumber, Long start) {
        validateJobConfig();
        Long safeStart = start == null || start < 0 ? 0L : start;
        String url = UriComponentsBuilder.fromHttpUrl(buildJobUrl(buildNumber + "/logText/progressiveText"))
                .queryParam("start", safeStart)
                .toUriString();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(buildAuthHeaders()), String.class);
            Long nextStart = parseLong(response.getHeaders().getFirst("X-Text-Size"), safeStart);
            Boolean moreData = Boolean.parseBoolean(response.getHeaders().getFirst("X-More-Data"));
            return new JenkinsConsoleChunk(StrUtil.nullToDefault(response.getBody(), ""), nextStart, moreData);
        } catch (RestClientException ex) {
            throw exception(PIPELINE_JENKINS_CONSOLE_FETCH_FAIL, StrUtil.subPre(ex.getMessage(), 500));
        }
    }

    private void validateTriggerConfig() {
        validateJobConfig();
        if (StrUtil.isBlank(properties.getCallbackUrl())) {
            throw exception(PIPELINE_JENKINS_CONFIG_INVALID, "callback-url 不能为空");
        }
        if (StrUtil.isBlank(properties.getCallbackToken())) {
            throw exception(PIPELINE_JENKINS_CONFIG_INVALID, "callback-token 不能为空");
        }
    }

    private void validateJobConfig() {
        validateBaseConfig();
        if (StrUtil.isBlank(properties.getJobName())) {
            throw exception(PIPELINE_JENKINS_CONFIG_INVALID, "job-name 不能为空");
        }
    }

    private void validateBaseConfig() {
        if (StrUtil.isBlank(properties.getBaseUrl())) {
            throw exception(PIPELINE_JENKINS_CONFIG_INVALID, "base-url 不能为空");
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        fillAuthHeaders(headers);
        return headers;
    }

    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        fillAuthHeaders(headers);
        return headers;
    }

    private void fillAuthHeaders(HttpHeaders headers) {
        if (StrUtil.isNotBlank(properties.getUsername()) && StrUtil.isNotBlank(properties.getApiToken())) {
            headers.setBasicAuth(properties.getUsername(), properties.getApiToken());
        }
    }

    private String buildJobUrl(String action) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(StrUtil.removeSuffix(properties.getBaseUrl(), "/"));
        for (String part : properties.getJobName().split("/")) {
            if (StrUtil.isNotBlank(part)) {
                builder.pathSegment("job", part);
            }
        }
        builder.pathSegment(action.split("/"));
        return builder.toUriString();
    }

    private List<JenkinsToolInstallation> fetchToolInstallations(String type, String[] descriptorNames) {
        for (String descriptorRoute : DESCRIPTOR_ROUTES) {
            for (String descriptorName : descriptorNames) {
                String url = buildDescriptorApiUrl(descriptorRoute, descriptorName);
                try {
                    ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET,
                            new HttpEntity<>(buildAuthHeaders()), JsonNode.class);
                    return parseToolInstallations(type, response.getBody());
                } catch (HttpClientErrorException ex) {
                    if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                        continue;
                    }
                    throw exception(PIPELINE_JENKINS_TOOL_FETCH_FAIL, StrUtil.subPre(ex.getMessage(), 500));
                } catch (RestClientException ex) {
                    throw exception(PIPELINE_JENKINS_TOOL_FETCH_FAIL, StrUtil.subPre(ex.getMessage(), 500));
                }
            }
        }
        return fetchToolInstallationsByScript(type);
    }

    private String buildDescriptorApiUrl(String descriptorRoute, String descriptorName) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(StrUtil.removeSuffix(properties.getBaseUrl(), "/"));
        for (String part : descriptorRoute.split("/")) {
            builder.pathSegment(part);
        }
        return builder.pathSegment(descriptorName, "api", "json")
                .queryParam("tree", "installations[name,home]").toUriString();
    }

    private List<JenkinsToolInstallation> fetchToolInstallationsByScript(String type) {
        String descriptorClassName = TOOL_TYPE_JDK.equals(type) ? SCRIPT_DESCRIPTOR_JDK : SCRIPT_DESCRIPTOR_MAVEN;
        String url = UriComponentsBuilder.fromHttpUrl(StrUtil.removeSuffix(properties.getBaseUrl(), "/"))
                .pathSegment("scriptText").toUriString();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("script", buildToolInstallationsScript(descriptorClassName));
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(form, buildHeaders()),
                    String.class);
            return parseScriptToolInstallations(type, response.getBody());
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return List.of();
            }
            throw exception(PIPELINE_JENKINS_TOOL_FETCH_FAIL, StrUtil.subPre(ex.getMessage(), 500));
        } catch (RestClientException ex) {
            throw exception(PIPELINE_JENKINS_TOOL_FETCH_FAIL, StrUtil.subPre(ex.getMessage(), 500));
        }
    }

    private String buildToolInstallationsScript(String descriptorClassName) {
        return """
                import groovy.json.JsonOutput
                import jenkins.model.Jenkins

                try {
                    def descriptorClass = Class.forName('%s')
                    def descriptor = Jenkins.get().getDescriptorByType(descriptorClass)
                    def installations = descriptor == null ? [] : descriptor.getInstallations()
                    println(JsonOutput.toJson(installations.collect { [name: it.name, home: it.home] }))
                } catch (ClassNotFoundException ignored) {
                    println('[]')
                }
                """.formatted(descriptorClassName);
    }

    private List<JenkinsToolInstallation> parseToolInstallations(String type, JsonNode root) {
        if (root == null || !root.path("installations").isArray()) {
            return List.of();
        }
        return parseToolInstallationArray(type, root.path("installations"));
    }

    private List<JenkinsToolInstallation> parseScriptToolInstallations(String type, String body) {
        if (StrUtil.isBlank(body)) {
            return List.of();
        }
        JsonNode root = JsonUtils.parseTree(body.trim());
        if (root == null || !root.isArray()) {
            return List.of();
        }
        return parseToolInstallationArray(type, root);
    }

    private List<JenkinsToolInstallation> parseToolInstallationArray(String type, JsonNode installations) {
        List<JenkinsToolInstallation> tools = new ArrayList<>();
        for (JsonNode installation : installations) {
            String name = text(installation, "name");
            if (StrUtil.isBlank(name)) {
                continue;
            }
            tools.add(new JenkinsToolInstallation(type, name, text(installation, "home")));
        }
        return tools;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || StrUtil.isBlank(field.asText())) {
            return null;
        }
        return field.asText();
    }

    private Long parseLong(String value, Long defaultValue) {
        try {
            return StrUtil.isBlank(value) ? defaultValue : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

}
