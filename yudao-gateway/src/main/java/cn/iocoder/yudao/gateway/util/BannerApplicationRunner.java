package cn.iocoder.yudao.gateway.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 项目启动成功后，输出启动提示。
 */
@Component
@Slf4j
public class BannerApplicationRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        log.info("\n----------------------------------------------------------\n\t" +
                "GONE项目启动成功\n" +
                "----------------------------------------------------------");
    }

}
