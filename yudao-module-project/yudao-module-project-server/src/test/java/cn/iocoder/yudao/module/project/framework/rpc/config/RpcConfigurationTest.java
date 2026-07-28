package cn.iocoder.yudao.module.project.framework.rpc.config;

import cn.iocoder.yudao.module.system.api.logger.OperateLogApi;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RpcConfigurationTest {

    @Test
    void testRegistersProjectFeignClients() {
        EnableFeignClients annotation = RpcConfiguration.class.getAnnotation(EnableFeignClients.class);

        assertEquals(Set.of(AdminUserApi.class, NotifyMessageSendApi.class, OperateLogApi.class, DictDataApi.class),
                Set.of(annotation.clients()));
    }

}
