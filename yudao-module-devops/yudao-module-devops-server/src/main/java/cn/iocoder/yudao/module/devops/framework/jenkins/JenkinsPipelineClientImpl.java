package cn.iocoder.yudao.module.devops.framework.jenkins;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_JENKINS_CONFIG_INVALID;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_JENKINS_CONSOLE_FETCH_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_JENKINS_TRIGGER_FAIL;

/**
 * Jenkins Pipeline HTTP client.
 */
@Component
public class JenkinsPipelineClientImpl implements JenkinsPipelineClient {

    @Resource
    private JenkinsProperties properties;
    @Resource
    private RestTemplate restTemplate;

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(properties.getEnabled());
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
        if (StrUtil.isBlank(properties.getBaseUrl())) {
            throw exception(PIPELINE_JENKINS_CONFIG_INVALID, "base-url 不能为空");
        }
        if (StrUtil.isBlank(properties.getJobName())) {
            throw exception(PIPELINE_JENKINS_CONFIG_INVALID, "job-name 不能为空");
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

    private Long parseLong(String value, Long defaultValue) {
        try {
            return StrUtil.isBlank(value) ? defaultValue : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

}
