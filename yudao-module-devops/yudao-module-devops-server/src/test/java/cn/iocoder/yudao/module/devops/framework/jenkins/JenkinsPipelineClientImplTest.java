package cn.iocoder.yudao.module.devops.framework.jenkins;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_JENKINS_CONSOLE_FETCH_FAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JenkinsPipelineClientImplTest extends BaseMockitoUnitTest {

    private JenkinsPipelineClientImpl client;

    @Mock
    private RestTemplate restTemplate;

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

}
