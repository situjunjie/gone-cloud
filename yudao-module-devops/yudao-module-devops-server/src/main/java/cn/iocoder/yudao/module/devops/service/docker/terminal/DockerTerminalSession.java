package cn.iocoder.yudao.module.devops.service.docker.terminal;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Docker 容器终端会话。
 */
public class DockerTerminalSession implements AutoCloseable {

    @Getter
    private final DockerClient client;
    @Getter
    private final String execId;
    @Getter
    private final InputStream stdin;
    private final OutputStream input;
    private ResultCallback<Frame> callback;

    public DockerTerminalSession(DockerClient client, String execId, InputStream stdin, OutputStream input) {
        this.client = client;
        this.execId = execId;
        this.stdin = stdin;
        this.input = input;
    }

    public void writeInput(String data) throws IOException {
        input.write(data.getBytes(StandardCharsets.UTF_8));
        input.flush();
    }

    public void resize(Integer cols, Integer rows) {
        if (cols == null || rows == null || cols <= 0 || rows <= 0) {
            return;
        }
        client.resizeExecCmd(execId).withSize(rows, cols).exec();
    }

    public void setCallback(ResultCallback<Frame> callback) {
        this.callback = callback;
    }

    @Override
    public void close() throws IOException {
        try {
            if (callback != null) {
                callback.close();
            }
        } finally {
            try {
                input.close();
            } finally {
                try {
                    stdin.close();
                } finally {
                    client.close();
                }
            }
        }
    }

}
