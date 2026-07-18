package cn.iocoder.yudao.module.devops.service.pipeline.plugin.dingtalk;

import cn.hutool.core.convert.Convert;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 基于 Hutool 的钉钉机器人客户端。
 */
@Component
public class HutoolDingTalkRobotClient implements DingTalkRobotClient {

    private static final int REQUEST_TIMEOUT_MILLIS = 5_000;

    @Override
    public void send(String webhookUrl, Map<String, Object> requestBody) {
        try (HttpResponse response = HttpRequest.post(webhookUrl)
                .header("Content-Type", "application/json; charset=UTF-8")
                .body(JsonUtils.toJsonString(requestBody))
                .timeout(REQUEST_TIMEOUT_MILLIS)
                .execute()) {
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new DingTalkRobotRequestException("钉钉机器人请求失败，HTTP 状态：" + response.getStatus());
            }
            Map<String, Object> responseBody = JsonUtils.parseMap(response.body());
            Integer errorCode = responseBody == null ? null : Convert.toInt(responseBody.get("errcode"));
            if (!Integer.valueOf(0).equals(errorCode)) {
                throw new DingTalkRobotRequestException("钉钉机器人返回失败，错误码：" + errorCode);
            }
        } catch (DingTalkRobotRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            // 外部 HTTP 异常可能在 message/cause 中携带完整请求 URL，统一截断为异常类型。
            throw new DingTalkRobotRequestException("钉钉机器人请求失败：" + ex.getClass().getSimpleName());
        }
    }

    private static class DingTalkRobotRequestException extends IllegalStateException {

        DingTalkRobotRequestException(String message) {
            super(message);
        }

    }

}
