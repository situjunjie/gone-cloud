package cn.iocoder.yudao.module.devops.service.kubernetes.terminal;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Kubernetes Pod 终端会话。
 */
@RequiredArgsConstructor
public class KubernetesTerminalSession implements AutoCloseable {

    @Getter
    private final KubernetesClient client;
    @Getter
    private final ExecWatch execWatch;

    public void writeInput(String data) throws IOException {
        OutputStream input = execWatch.getInput();
        if (input == null) {
            return;
        }
        input.write(data.getBytes(StandardCharsets.UTF_8));
        input.flush();
    }

    public void resize(Integer cols, Integer rows) {
        if (cols == null || rows == null || cols <= 0 || rows <= 0) {
            return;
        }
        execWatch.resize(cols, rows);
    }

    @Override
    public void close() {
        execWatch.close();
        client.close();
    }

}
