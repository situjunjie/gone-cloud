package cn.iocoder.yudao.module.project.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum ProjectWorkItemQueryOperatorEnum {

    EQ("eq"),
    NE("ne"),
    GT("gt"),
    GE("ge"),
    LT("lt"),
    LE("le"),
    BETWEEN("between"),
    IN("in"),
    NOT_IN("not_in"),
    CONTAINS("contains"),
    NOT_CONTAINS("not_contains");

    private final String operator;

    public static ProjectWorkItemQueryOperatorEnum of(String operator) {
        return Arrays.stream(values())
                .filter(item -> item.getOperator().equals(operator))
                .findFirst()
                .orElse(null);
    }

}
