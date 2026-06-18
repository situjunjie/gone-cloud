package cn.iocoder.yudao.module.devops.framework.docker.terminal;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.framework.websocket.core.util.WebSocketFrameworkUtils;
import cn.iocoder.yudao.module.devops.service.docker.terminal.DockerTerminalService;
import cn.iocoder.yudao.module.devops.service.docker.terminal.DockerTerminalSession;
import cn.iocoder.yudao.module.devops.service.kubernetes.terminal.KubernetesTerminalMessage;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
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

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static cn.iocoder.yudao.module.devops.service.kubernetes.terminal.KubernetesTerminalMessage.TYPE_CLOSED;
import static cn.iocoder.yudao.module.devops.service.kubernetes.terminal.KubernetesTerminalMessage.TYPE_CLOSE;
import static cn.iocoder.yudao.module.devops.service.kubernetes.terminal.KubernetesTerminalMessage.TYPE_ERROR;
import static cn.iocoder.yudao.module.devops.service.kubernetes.terminal.KubernetesTerminalMessage.TYPE_INPUT;
import static cn.iocoder.yudao.module.devops.service.kubernetes.terminal.KubernetesTerminalMessage.TYPE_OUTPUT;
import static cn.iocoder.yudao.module.devops.service.kubernetes.terminal.KubernetesTerminalMessage.TYPE_RESIZE;

/**
 * Docker 容器终端 WebSocket 处理器。
 */
@Slf4j
@Component
public class DockerTerminalWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTRIBUTE_TERMINAL_CONNECTION = "DOCKER_TERMINAL_CONNECTION";

    @Resource
    private DockerTerminalService dockerTerminalService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        DockerTerminalSession terminalSession = null;
        try {
            MultiValueMap<String, String> params = parseQueryParams(session.getUri());
            terminalSession = openTerminalWithTenant(session, params);
            TerminalConnection connection = new TerminalConnection(session, terminalSession);
            session.getAttributes().put(ATTRIBUTE_TERMINAL_CONNECTION, connection);
            ResultCallback<Frame> callback = terminalSession.getClient().execStartCmd(terminalSession.getExecId())
                    .withTty(true)
                    .withStdIn(terminalSession.getStdin())
                    .exec(new TerminalFrameCallback(connection));
            connection.setCallback(callback);
            terminalSession.setCallback(callback);
        } catch (ServiceException ex) {
            sendMessage(session, TYPE_ERROR, null, null, ex.getMessage());
            session.close(CloseStatus.POLICY_VIOLATION.withReason(StrUtil.subPre(ex.getMessage(), 120)));
        } catch (Exception ex) {
            if (terminalSession != null) {
                try {
                    terminalSession.close();
                } catch (Exception closeEx) {
                    log.warn("[afterConnectionEstablished][session({}) close Docker terminal after open failure]",
                            session.getId(), closeEx);
                }
            }
            log.error("[afterConnectionEstablished][uri({}) Docker terminal open failed]", session.getUri(), ex);
            sendMessage(session, TYPE_ERROR, null, null, "Docker 终端连接失败");
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
        log.warn("[handleTransportError][session({}) Docker terminal transport error]", session.getId(), exception);
        closeTerminal(session, "transport error", true);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeTerminal(session, "websocket closed", false);
    }

    private DockerTerminalSession openTerminalWithTenant(WebSocketSession session, MultiValueMap<String, String> params)
            throws Exception {
        DockerTerminalSession[] terminalSession = new DockerTerminalSession[1];
        executeWithTenant(session, () -> terminalSession[0] = dockerTerminalService.openTerminal(
                parseLong(params.getFirst("environmentId")), params.getFirst("containerId")));
        return terminalSession[0];
    }

    private MultiValueMap<String, String> parseQueryParams(URI uri) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams();
    }

    private Long parseLong(String value) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("environmentId 不能为空");
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
        closeQuietly(connection.getCallback());
        try {
            connection.getTerminalSession().close();
        } catch (Exception ex) {
            log.warn("[closeTerminal][session({}) close Docker terminal failed]", session.getId(), ex);
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

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // ignore
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
        private final DockerTerminalSession terminalSession;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private ResultCallback<Frame> callback;

        void setCallback(ResultCallback<Frame> callback) {
            this.callback = callback;
        }

    }

    private class TerminalFrameCallback extends ResultCallback.Adapter<Frame> {

        private final TerminalConnection connection;

        TerminalFrameCallback(TerminalConnection connection) {
            this.connection = connection;
        }

        @Override
        public void onNext(Frame frame) {
            if (connection.getClosed().get() || frame == null || frame.getPayload() == null) {
                return;
            }
            sendQuietly(connection.getWebSocketSession(), TYPE_OUTPUT,
                    new String(frame.getPayload(), StandardCharsets.UTF_8), null, null);
        }

        @Override
        public void onError(Throwable throwable) {
            if (!connection.getClosed().get()) {
                sendQuietly(connection.getWebSocketSession(), TYPE_ERROR, null, null, "Docker 终端输出读取失败");
                closeTerminal(connection.getWebSocketSession(), "output failed", true);
            }
        }

        @Override
        public void onComplete() {
            closeTerminal(connection.getWebSocketSession(), "process exited", true);
        }

    }

    @FunctionalInterface
    private interface TenantRunnable {

        void run() throws Exception;

    }

}
