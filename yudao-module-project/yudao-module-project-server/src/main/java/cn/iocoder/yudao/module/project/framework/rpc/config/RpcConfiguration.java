package cn.iocoder.yudao.module.project.framework.rpc.config;

import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.logger.OperateLogApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "projectRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {AdminUserApi.class, NotifyMessageSendApi.class, OperateLogApi.class, DictDataApi.class})
public class RpcConfiguration {
}
