package cn.iocoder.yudao.module.devops.framework.host.terminal;

import cn.iocoder.yudao.framework.security.config.AuthorizeRequestsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * HOST 主机终端 WebSocket 配置。
 */
@Configuration
public class HostTerminalWebSocketConfiguration {

    public static final String TERMINAL_PATH = "/devops/host/terminal";

    @Bean
    public WebSocketConfigurer hostTerminalWebSocketConfigurer(
            HostTerminalWebSocketHandler handler,
            HandshakeInterceptor[] handshakeInterceptors) {
        return registry -> registry.addHandler(handler, TERMINAL_PATH)
                .addInterceptors(handshakeInterceptors)
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public AuthorizeRequestsCustomizer hostTerminalAuthorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {
            @Override
            public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                registry.requestMatchers(TERMINAL_PATH).permitAll();
            }
        };
    }

}
