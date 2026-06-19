package cn.iocoder.yudao.module.devops.framework.rpc.config;

import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.websocket.WebSocketSenderApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "devopsRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {ConfigApi.class, FileApi.class, WebSocketSenderApi.class, BpmProcessInstanceApi.class})
public class RpcConfiguration {
}
