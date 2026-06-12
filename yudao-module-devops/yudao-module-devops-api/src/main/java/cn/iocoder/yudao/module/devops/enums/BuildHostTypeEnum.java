package cn.iocoder.yudao.module.devops.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 构建主机类型。
 *
 * <p>构建主机是流水线 BUILD 类节点执行 shell 的运行机，与「部署目标环境」语义不同：
 * SSH 为主路径（远程专用构建机），LOCAL 为退化特例（平台本机 ProcessBuilder）。
 */
@Getter
@AllArgsConstructor
public enum BuildHostTypeEnum implements ArrayValuable<String> {

    LOCAL("LOCAL", "本机"),
    SSH("SSH", "远程 SSH 构建机");

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(BuildHostTypeEnum::getType)
            .toArray(String[]::new);

    private final String type;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

    public static boolean isSsh(String type) {
        return SSH.type.equals(type);
    }

    public static boolean isLocal(String type) {
        return LOCAL.type.equals(type);
    }

}
