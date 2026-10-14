package cn.iocoder.yudao.module.devops.framework.common;

/**
 * 敏感键判定工具。
 *
 * <p>流水线输入上下文过滤与部署配置展示脱敏共用同一套规则，避免两处判定漂移。
 */
public final class SensitiveKeyUtils {

    private SensitiveKeyUtils() {
    }

    /**
     * 判断键名是否语义敏感（密码、密钥、令牌、凭据）。
     *
     * @param key 键名；允许为 {@code null}
     * @return {@code true} 表示展示或记录时应遮蔽其值
     */
    public static boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("password") || lowerKey.contains("secret") || lowerKey.contains("token")
                || lowerKey.contains("credential") || lowerKey.contains("passwd") || lowerKey.contains("pwd");
    }

}
