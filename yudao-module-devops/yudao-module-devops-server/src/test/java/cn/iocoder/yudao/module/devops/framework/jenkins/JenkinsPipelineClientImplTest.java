package cn.iocoder.yudao.module.devops.framework.jenkins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_JENKINS_CONSOLE_FETCH_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_JENKINS_TOOL_FETCH_FAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JenkinsPipelineClientImplTest extends BaseMockitoUnitTest {

    private JenkinsPipelineClientImpl client;

    @Mock
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        JenkinsProperties properties = new JenkinsProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://jenkins.example.com/");
        properties.setJobName("folder/demo");
        properties.setUsername("admin");
        properties.setApiToken("token");

        client = new JenkinsPipelineClientImpl();
        ReflectionTestUtils.setField(client, "properties", properties);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
    }

    @Test
    public void testGetToolInstallations_allTypes() throws Exception {
        // 准备参数
        when(restTemplate.exchange(argThat((String url) -> contains(url, "/descriptorByName/hudson.model.JDK/api/json")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(json("""
                        {"installations":[
                          {"name":"jdk-17.0.12","home":"/opt/jdk-17.0.12"},
                          {"name":" ","home":"/invalid"}
                        ]}
                        """)));
        when(restTemplate.exchange(argThat((String url) -> contains(url, "/descriptorByName/hudson.tasks.Maven$MavenInstallation/api/json")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(json("""
                        {"installations":[{"name":"maven-3.9.9","home":""}]}
                        """)));

        // 调用
        List<JenkinsToolInstallation> tools = client.getToolInstallations(null);

        // 断言
        assertEquals(2, tools.size());
        assertEquals("JDK", tools.get(0).getType());
        assertEquals("jdk-17.0.12", tools.get(0).getName());
        assertEquals("/opt/jdk-17.0.12", tools.get(0).getHome());
        assertEquals("MAVEN", tools.get(1).getType());
        assertEquals("maven-3.9.9", tools.get(1).getName());
        assertNull(tools.get(1).getHome());
    }

    @Test
    public void testGetToolInstallations_filterMavenDescriptorNotFound() {
        // 准备参数
        when(restTemplate.exchange(argThat((String url) -> contains(url, "/descriptorByName/")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));
        when(restTemplate.exchange(argThat((String url) -> contains(url, "/manage/descriptorByName/")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));
        when(restTemplate.postForEntity(eq("http://jenkins.example.com/scriptText"), any(HttpEntity.class),
                eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        // 调用
        List<JenkinsToolInstallation> tools = client.getToolInstallations("maven");

        // 断言
        assertTrue(tools.isEmpty());
    }

    @Test
    public void testGetToolInstallations_descriptorImplFallback() throws Exception {
        // 准备参数
        when(restTemplate.exchange(argThat((String url) -> contains(url, "/descriptorByName/hudson.model.JDK/api/json")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));
        when(restTemplate.exchange(argThat((String url) -> contains(url, "/descriptorByName/hudson.model.JDK$DescriptorImpl/api/json")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(json("""
                        {"installations":[{"name":"jdk-21","home":"/opt/jdk-21"}]}
                        """)));

        // 调用
        List<JenkinsToolInstallation> tools = client.getToolInstallations("JDK");

        // 断言
        assertEquals(1, tools.size());
        assertEquals("JDK", tools.get(0).getType());
        assertEquals("jdk-21", tools.get(0).getName());
        assertEquals("/opt/jdk-21", tools.get(0).getHome());
    }

    @Test
    public void testGetToolInstallations_manageDescriptorFallback() throws Exception {
        // 准备参数
        when(restTemplate.exchange(argThat((String url) -> contains(url, "/descriptorByName/")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));
        when(restTemplate.exchange(argThat((String url) -> contains(url, "/manage/descriptorByName/hudson.model.JDK/api/json")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(json("""
                        {"installations":[{"name":"jdk-manage","home":"/opt/jdk-manage"}]}
                        """)));

        // 调用
        List<JenkinsToolInstallation> tools = client.getToolInstallations("JDK");

        // 断言
        assertEquals(1, tools.size());
        assertEquals("jdk-manage", tools.get(0).getName());
        assertEquals("/opt/jdk-manage", tools.get(0).getHome());
    }

    @Test
    public void testGetToolInstallations_scriptFallback() {
        // 准备参数
        when(restTemplate.exchange(argThat((String url) -> contains(url, "/descriptorByName/")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));
        when(restTemplate.exchange(argThat((String url) -> contains(url, "/manage/descriptorByName/")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));
        when(restTemplate.postForEntity(eq("http://jenkins.example.com/scriptText"), any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        [{"name":"jdk-script","home":"/opt/jdk-script"}]
                        """));

        // 调用
        List<JenkinsToolInstallation> tools = client.getToolInstallations("JDK");

        // 断言
        assertEquals(1, tools.size());
        assertEquals("JDK", tools.get(0).getType());
        assertEquals("jdk-script", tools.get(0).getName());
        assertEquals("/opt/jdk-script", tools.get(0).getHome());
    }

    @Test
    public void testGetToolInstallations_fetchFail() {
        // 准备参数
        when(restTemplate.exchange(argThat((String url) -> contains(url, "/descriptorByName/hudson.model.JDK/api/json")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("connect fail"));

        // 调用 & 断言
        assertServiceException(() -> client.getToolInstallations("JDK"),
                PIPELINE_JENKINS_TOOL_FETCH_FAIL, "connect fail");
    }

    @Test
    public void testGetConsoleText_success() {
        // 准备参数
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Text-Size", "42");
        headers.add("X-More-Data", "true");
        String url = "http://jenkins.example.com/job/folder/job/demo/58/logText/progressiveText?start=12";
        when(restTemplate.exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("line 1\n", headers, 200));

        // 调用
        JenkinsConsoleChunk chunk = client.getConsoleText("58", 12L);

        // 断言
        assertEquals("line 1\n", chunk.getText());
        assertEquals(42L, chunk.getNextStart());
        assertTrue(chunk.getMoreData());
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(url), eq(HttpMethod.GET), entityCaptor.capture(), eq(String.class));
        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString("admin:token".getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedAuth, entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    public void testGetConsoleText_missingHeadersUseDefaults() {
        // 准备参数
        String url = "http://jenkins.example.com/job/folder/job/demo/58/logText/progressiveText?start=0";
        when(restTemplate.exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(null));

        // 调用
        JenkinsConsoleChunk chunk = client.getConsoleText("58", -1L);

        // 断言
        assertEquals("", chunk.getText());
        assertEquals(0L, chunk.getNextStart());
        assertFalse(chunk.getMoreData());
    }

    @Test
    public void testGetConsoleText_fetchFail() {
        // 准备参数
        String url = "http://jenkins.example.com/job/folder/job/demo/58/logText/progressiveText?start=0";
        when(restTemplate.exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("connect fail"));

        // 调用 & 断言
        assertServiceException(() -> client.getConsoleText("58", 0L),
                PIPELINE_JENKINS_CONSOLE_FETCH_FAIL, "connect fail");
    }

    private JsonNode json(String content) throws Exception {
        return objectMapper.readTree(content);
    }

    private boolean contains(String value, String part) {
        return value != null && value.contains(part);
    }

}
