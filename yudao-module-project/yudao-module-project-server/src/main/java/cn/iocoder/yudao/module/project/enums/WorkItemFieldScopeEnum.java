package cn.iocoder.yudao.module.project.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

@Getter
@AllArgsConstructor
public enum WorkItemFieldScopeEnum implements ArrayValuable<String> {

    GLOBAL("GLOBAL"),
    PROJECT("PROJECT");

    public static final Long GLOBAL_PROJECT_ID = 0L;
    public static final String[] ARRAYS = Arrays.stream(values()).map(WorkItemFieldScopeEnum::getValue)
            .toArray(String[]::new);

    private final String value;

    @Override
    public String[] array() {
        return ARRAYS;
    }

    public static boolean isGlobal(String scope) {
        return GLOBAL.value.equals(scope);
    }

    public static boolean isEffectiveForProject(String scope, Long fieldProjectId, Long projectId) {
        return (isGlobal(scope) && Objects.equals(fieldProjectId, GLOBAL_PROJECT_ID))
                || (PROJECT.value.equals(scope) && Objects.equals(fieldProjectId, projectId));
    }

}
