package cn.iocoder.yudao.module.devops.service.variablegroup;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class VariableGroupParser {
    private static final Pattern KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private VariableGroupParser() {}
    public static Map<String, String> parse(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        String[] lines = text == null ? new String[0] : text.replace("\r\n", "\n").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty()) continue;
            int eq = line.indexOf('=');
            String key = eq < 0 ? "" : line.substring(0, eq).trim();
            if (eq < 0 || !KEY.matcher(key).matches()) throw new IllegalArgumentException(String.valueOf(i + 1));
            if (result.containsKey(key)) throw new IllegalStateException(String.valueOf(i + 1));
            result.put(key, line.substring(eq + 1));
        }
        return result;
    }
}
