package cn.iocoder.yudao.module.devops.framework.host.terminal;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.framework.websocket.core.util.WebSocketFrameworkUtils;
import cn.iocoder.yudao.module.devops.service.host.terminal.HostTerminalService;
import cn.iocoder.yudao.module.devops.service.host.terminal.HostTerminalSession;
import cn.iocoder.yudao.module.devops.service.kubernetes.terminal.KubernetesTerminalMessage;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static cn.iocoder.yudao.module.devops.service.kubernetes.terminal.KubernetesTerminalMessage.TYPE_CLOSED;
import static cn.iocoder.yudao.module.devops.service.kubernetes.terminal.KubernetesTerminalMessage.TYPE_CLOSE;
import static cn.iocoder.yudao.module.devops.service.kubernetes.terminal.KubernetesTerminalMessage.TYPE_ERROR;
import static cn.iocoder.yudao.module.devops.service.kubernetes.terminal.KubernetesTerminalMessage.TYPE_INPUT;
import static cn.iocoder.yudao.module.devops.service.kubernetes.terminal.KubernetesTerminalMessage.TYPE_OUTPUT;
import static cn.iocoder.yudao.module.devops.service.kubernetes.terminal.KubernetesTerminalMessage.TYPE_RESIZE;

/**
 * HOST 主机终端 WebSocket 处理器。
 */
@Slf4j
@Component
public class HostTerminalWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTRIBUTE_TERMINAL_CONNECTION = "HOST_TERMINAL_CONNECTION";

    @Resource
    private HostTerminalService hostTerminalService;

    private final ExecutorService terminalExecutor = Executors.newCachedThreadPool();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            MultiValueMap<String, String> params = parseQueryParams(session.getUri());
            HostTerminalSession terminalSession = openTerminalWithTenant(session, params);
            TerminalConnection connection = new TerminalConnection(session, terminalSession);
            session.getAttributes().put(ATTRIBUTE_TERMINAL_CONNECTION, connection);
            startOutputForwarding(connection);
        } catch (ServiceException ex) {
            sendMessage(session, TYPE_ERROR, null, null, ex.getMessage());
            session.close(CloseStatus.POLICY_VIOLATION.withReason(StrUtil.subPre(ex.getMessage(), 120)));
        } catch (Exception ex) {
            log.error("[afterConnectionEstablished][uri({}) HOST terminal open failed]", session.getUri(), ex);
            sendMessage(session, TYPE_ERROR, null, null, "HOST 终端连接失败");
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        TerminalConnection connection = getConnection(session);
        if (connection == null || connection.getClosed().get()) {
            return;
        }
        KubernetesTerminalMessage terminalMessage = JsonUtils.parseObjectQuietly(message.getPayload(),
                KubernetesTerminalMessage.class);
        if (terminalMessage == null || StrUtil.isBlank(terminalMessage.getType())) {
            sendMessage(session, TYPE_ERROR, null, null, "终端消息格式无效");
            return;
        }
        if (TYPE_INPUT.equals(terminalMessage.getType())) {
            executeWithTenant(session,
                    () -> connection.getTerminalSession().writeInput(StrUtil.nullToDefault(terminalMessage.getData(), "")));
            return;
        }
        if (TYPE_RESIZE.equals(terminalMessage.getType())) {
            executeWithTenant(session, () -> connection.getTerminalSession().resize(terminalMessage.getCols(),
                    terminalMessage.getRows()));
            return;
        }
        if (TYPE_CLOSE.equals(terminalMessage.getType())) {
            closeTerminal(session, "client closed", true);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("[handleTransportError][session({}) HOST terminal transport error]", session.getId(), exception);
        closeTerminal(session, "transport error", true);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeTerminal(session, "websocket closed", false);
    }

    @PreDestroy
    public void destroy() {
        terminalExecutor.shutdownNow();
    }

    private void startOutputForwarding(TerminalConnection connection) {
        terminalExecutor.execute(() -> forwardOutput(connection));
    }

    private HostTerminalSession openTerminalWithTenant(WebSocketSession session, MultiValueMap<String, String> params)
            throws Exception {
        HostTerminalSession[] terminalSession = new HostTerminalSession[1];
        executeWithTenant(session, () -> terminalSession[0] = hostTerminalService.openTerminal(
                parseLong(params.getFirst("environmentId"), "environmentId"),
                parseLong(params.getFirst("hostId"), "hostId")));
        return terminalSession[0];
    }

    private void forwardOutput(TerminalConnection connection) {
        InputStream stream = connection.getTerminalSession().getOutput();
        byte[] buffer = new byte[4096];
        try {
            int length;
            while (!connection.getClosed().get() && (length = stream.read(buffer)) != -1) {
                if (length > 0) {
                    sendMessage(connection.getWebSocketSession(), TYPE_OUTPUT,
                            new String(buffer, 0, length, StandardCharsets.UTF_8), null, null);
                }
            }
            closeTerminal(connection.getWebSocketSession(), "process exited", true);
        } catch (IOException ex) {
            if (!connection.getClosed().get()) {
                log.warn("[forwardOutput][session({}) HOST terminal output failed]",
                        connection.getWebSocketSession().getId(), ex);
                sendQuietly(connection.getWebSocketSession(), TYPE_ERROR, null, null, "终端输出读取失败");
                closeTerminal(connection.getWebSocketSession(), "output failed", true);
            }
        }
    }

    private MultiValueMap<String, String> parseQueryParams(URI uri) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams();
    }

    private Long parseLong(String value, String name) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return Long.valueOf(value);
    }

    private TerminalConnection getConnection(WebSocketSession session) {
        return (TerminalConnection) session.getAttributes().get(ATTRIBUTE_TERMINAL_CONNECTION);
    }

    private void executeWithTenant(WebSocketSession session, TenantRunnable runnable) throws Exception {
        Long tenantId = WebSocketFrameworkUtils.getTenantId(session);
        if (tenantId == null) {
            runnable.run();
            return;
        }
        Exception[] exception = new Exception[1];
        TenantUtils.execute(tenantId, () -> {
            try {
                runnable.run();
            } catch (Exception ex) {
                exception[0] = ex;
            }
        });
        if (exception[0] != null) {
            throw exception[0];
        }
    }

    private void closeTerminal(WebSocketSession session, String reason, boolean closeWebSocket) {
        TerminalConnection connection = getConnection(session);
        if (connection == null || !connection.getClosed().compareAndSet(false, true)) {
            return;
        }
        try {
            connection.getTerminalSession().close();
        } catch (Exception ex) {
            log.warn("[closeTerminal][session({}) close HOST terminal failed]", session.getId(), ex);
        }
        sendQuietly(session, TYPE_CLOSED, null, reason, null);
        if (closeWebSocket && session.isOpen()) {
            try {
                session.close(CloseStatus.NORMAL.withReason(reason));
            } catch (IOException ex) {
                log.warn("[closeTerminal][session({}) close websocket failed]", session.getId(), ex);
            }
        }
    }

    private void sendQuietly(WebSocketSession session, String type, String data, String reason, String message) {
        try {
            sendMessage(session, type, data, reason, message);
        } catch (IOException ex) {
            log.warn("[sendQuietly][session({}) send terminal message failed]", session.getId(), ex);
        }
    }

    private void sendMessage(WebSocketSession session, String type, String data, String reason, String message)
            throws IOException {
        if (!session.isOpen()) {
            return;
        }
        KubernetesTerminalMessage terminalMessage = new KubernetesTerminalMessage();
        terminalMessage.setType(type);
        terminalMessage.setData(data);
        terminalMessage.setReason(reason);
        terminalMessage.setMessage(message);
        synchronized (session) {
            session.sendMessage(new TextMessage(JsonUtils.toJsonString(terminalMessage)));
        }
    }

    @Getter
    @RequiredArgsConstructor
    private static class TerminalConnection {

        private final WebSocketSession webSocketSession;
        private final HostTerminalSession terminalSession;
        private final AtomicBoolean closed = new AtomicBoolean(false);

    }

    @FunctionalInterface
    private interface TenantRunnable {

        void run() throws Exception;

    }

}
