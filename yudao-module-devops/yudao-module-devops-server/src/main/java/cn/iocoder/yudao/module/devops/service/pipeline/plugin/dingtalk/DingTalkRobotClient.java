package cn.iocoder.yudao.module.devops.service.pipeline.plugin.dingtalk;

import java.util.Map;

/**
 * 钉钉机器人 HTTP 客户端。
 */
public interface DingTalkRobotClient {

    /**
     * 发送机器人消息。
     *
     * @param webhookUrl webhook 地址
     * @param requestBody 请求体
     */
    void send(String webhookUrl, Map<String, Object> requestBody);

}
