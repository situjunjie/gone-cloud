package cn.iocoder.yudao.module.devops.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum VariableGroupScopeEnum implements ArrayValuable<String> {
    GLOBAL("GLOBAL"), APPLICATION("APPLICATION"), APPLICATION_ENV("APPLICATION_ENV");
    private final String scope;
    @Override public String[] array() { return new String[]{GLOBAL.scope, APPLICATION.scope, APPLICATION_ENV.scope}; }
}
