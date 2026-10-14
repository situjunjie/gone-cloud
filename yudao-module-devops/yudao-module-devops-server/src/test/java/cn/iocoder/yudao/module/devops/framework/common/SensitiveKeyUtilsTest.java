package cn.iocoder.yudao.module.devops.framework.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SensitiveKeyUtils} 的单元测试。
 */
class SensitiveKeyUtilsTest {

    @Test
    void testIsSensitive_sensitiveKeys() {
        assertTrue(SensitiveKeyUtils.isSensitive("DB_PASSWORD"));
        assertTrue(SensitiveKeyUtils.isSensitive("apiSecret"));
        assertTrue(SensitiveKeyUtils.isSensitive("access_token"));
        assertTrue(SensitiveKeyUtils.isSensitive("CREDENTIAL"));
        assertTrue(SensitiveKeyUtils.isSensitive("sshPasswd"));
        assertTrue(SensitiveKeyUtils.isSensitive("pwd"));
    }

    @Test
    void testIsSensitive_nonSensitiveKeys() {
        assertFalse(SensitiveKeyUtils.isSensitive(null));
        assertFalse(SensitiveKeyUtils.isSensitive("DB_HOST"));
        assertFalse(SensitiveKeyUtils.isSensitive("IMAGE"));
        assertFalse(SensitiveKeyUtils.isSensitive("NAMESPACE"));
    }

}
