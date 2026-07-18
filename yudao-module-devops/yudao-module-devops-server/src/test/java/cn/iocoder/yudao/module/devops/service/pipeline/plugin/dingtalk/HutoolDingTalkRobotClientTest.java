package cn.iocoder.yudao.module.devops.service.pipeline.plugin.dingtalk;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HutoolDingTalkRobotClientTest {

    private HttpServer server;
    private HutoolDingTalkRobotClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        client = new HutoolDingTalkRobotClient();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void testSend_success() {
        server.createContext("/robot/send", exchange -> respond(exchange, 200, "{\"errcode\":0}"));

        assertDoesNotThrow(() -> client.send(url("safe-token"), Map.of("msgtype", "text")));
    }

    @Test
    void testSend_businessErrorOnlyExposesErrorCode() {
        server.createContext("/robot/send", exchange -> respond(exchange, 200,
                "{\"errcode\":310000,\"errmsg\":\"access_token=sensitive-token\"}"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.send(url("sensitive-token"), Map.of("secret", "sensitive-body")));

        assertEquals("钉钉机器人返回失败，错误码：310000", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testSend_transportErrorDoesNotExposeRequestUrl() {
        String sensitiveUrl = "not-a-valid-url?access_token=sensitive-token";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.send(sensitiveUrl, Map.of("secret", "sensitive-body")));

        assertFalse(exception.getMessage().contains("sensitive-token"));
        assertFalse(exception.getMessage().contains("sensitive-body"));
        assertNull(exception.getCause());
    }

    private String url(String accessToken) {
        return "http://127.0.0.1:" + server.getAddress().getPort()
                + "/robot/send?access_token=" + accessToken;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

}
