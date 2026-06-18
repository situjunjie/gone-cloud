package cn.iocoder.yudao.module.devops.service.host.terminal;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;
import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * HOST 主机终端会话。
 */
public class HostTerminalSession implements AutoCloseable {

    private static final int DEFAULT_WIDTH_PIXELS = 960;
    private static final int DEFAULT_HEIGHT_PIXELS = 640;

    private final Session session;
    private final ChannelShell channel;
    @Getter
    private final InputStream output;
    private final OutputStream input;

    public HostTerminalSession(Session session, ChannelShell channel, InputStream output, OutputStream input) {
        this.session = session;
        this.channel = channel;
        this.output = output;
        this.input = input;
    }

    public void writeInput(String data) throws IOException {
        input.write(data.getBytes(StandardCharsets.UTF_8));
        input.flush();
    }

    public void resize(Integer cols, Integer rows) {
        if (cols == null || rows == null || cols <= 0 || rows <= 0 || channel.isClosed()) {
            return;
        }
        channel.setPtySize(cols, rows, DEFAULT_WIDTH_PIXELS, DEFAULT_HEIGHT_PIXELS);
    }

    @Override
    public void close() throws IOException {
        try {
            input.close();
        } finally {
            try {
                output.close();
            } finally {
                try {
                    channel.disconnect();
                } finally {
                    session.disconnect();
                }
            }
        }
    }

}
